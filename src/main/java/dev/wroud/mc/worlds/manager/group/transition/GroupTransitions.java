package dev.wroud.mc.worlds.manager.group.transition;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.McWorld;
import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.abstractions.TeleportTransitionAbstraction;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.mixin.groups.PlayerListAccessor;
import dev.wroud.mc.worlds.mixin.groups.ServerPlayerAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class GroupTransitions {
  private final MinecraftServer server;
  private final WorldGroups groups;
  private final GroupProfiles profiles;
  private final GroupSwitch switcher;
  private final Map<ServerPlayer, TeleportTransition> pending = new ConcurrentHashMap<>();
  private final Set<String> reportedPaths = ConcurrentHashMap.newKeySet();
  private final Set<Identifier> deletedGroups = ConcurrentHashMap.newKeySet();
  private final Set<UUID> leaving = ConcurrentHashMap.newKeySet();

  public GroupTransitions(MinecraftServer server, WorldGroups groups) {
    this.server = server;
    this.groups = groups;
    this.profiles = new GroupProfiles(server, groups);
    this.switcher = new GroupSwitch(this.profiles);
  }

  public static void bootstrap() {
    ServerPlayerEvents.JOIN.register(player -> {
      if (GroupProfiles.markerOf(player) == null) {
        player.setAttached(GroupProfiles.MARKER, WorldGroups.groupOrDefault(player.level()));
      }
    });
  }

  public static @Nullable GroupTransitions of(MinecraftServer server) {
    var groups = WorldGroups.of(server);
    return groups != null ? groups.transitions() : null;
  }

  public static Identifier groupOf(ServerPlayer player) {
    return GroupProfiles.groupOf(player);
  }

  private static boolean needsSwitch(ServerPlayer player, ServerLevel level) {
    return !GroupProfiles.groupOf(player).equals(WorldGroups.groupOrDefault(level));
  }

  public boolean queue(ServerPlayer player, TeleportTransition transition) {
    if (player.isRemoved() || !needsSwitch(player, transition.newLevel())) {
      return false;
    }
    if (player.isDeadOrDying()) {
      McWorldMod.LOGGER.info("{} is dead, the teleport to {} in another group is dropped until the respawn",
          player.getPlainTextName(), transition.newLevel().dimension().identifier());
      return true;
    }
    this.pending.put(player, transition);
    return true;
  }

  public boolean afterSwitch(ServerPlayer player, TeleportTransition.PostTeleportTransition action) {
    var transition = this.pending.get(player);
    if (transition == null) {
      return false;
    }
    this.pending.put(player, new TeleportTransition(transition.newLevel(), transition.position(),
        transition.deltaMovement(), transition.yRot(), transition.xRot(), transition.missingRespawnBlock(),
        transition.asPassenger(), transition.relatives(), transition.postTeleportTransition().then(action)));
    return true;
  }

  public boolean returnHome(ServerPlayer player) {
    var queued = this.pending.get(player);
    var entering = queued != null && TemporaryIds.isTemporary(WorldGroups.groupOrDefault(queued.newLevel()));
    if (entering) {
      this.pending.remove(player);
    }
    if (!TemporaryIds.isTemporary(GroupProfiles.groupOf(player))) {
      return entering;
    }
    if (player.isDeadOrDying()) {
      this.leaving.add(player.getUUID());
      return true;
    }
    var sent = player.teleport(this.homeTransition(player)) != null;
    if (this.pending.containsKey(player)) {
      this.leaving.add(player.getUUID());
    }
    return sent;
  }

  public boolean evacuate(ServerPlayer player, ServerLevel stopping) {
    if (this.pending.containsKey(player)) {
      return true;
    }
    var group = GroupProfiles.groupOf(player);
    var goHome = TemporaryIds.isTemporary(group) && (this.isClosed(group)
        || this.groups.spawnOf(group).spawn().dimension().equals(stopping.dimension()));
    if (goHome) {
      player.teleport(this.homeTransition(player));
    }
    return goHome;
  }

  private boolean isClosed(Identifier group) {
    var target = this.groups.get(group);
    return target == null || target.members().isEmpty();
  }

  private TeleportTransition homeTransition(ServerPlayer player) {
    var home = this.profiles.record(player.getUUID()).flatMap(this.profiles::validHome);
    var level = home.map(GroupProfiles::dimensionOf).map(this::liveLevel).orElse(null);
    var saved = home.map(GroupProfiles::positionOf).orElse(ServerPlayer.SavedPosition.EMPTY);
    if (level == null || saved.position().isEmpty()) {
      return TeleportTransitionAbstraction.spawnNear(player, this.server.findRespawnDimension(),
          this.server.getRespawnData(), TeleportTransition.DO_NOTHING);
    }
    var rotation = saved.rotation().orElse(Vec2.ZERO);
    return new TeleportTransition(level, saved.position().get(), Vec3.ZERO, rotation.x, rotation.y,
        TeleportTransition.DO_NOTHING);
  }

  private @Nullable ServerLevel liveLevel(ResourceKey<Level> dimension) {
    var level = this.server.getLevel(dimension);
    return level instanceof CustomServerLevel custom && custom.isDeleteOnClose() ? null : level;
  }

  public void verify(ServerPlayer player, ServerLevel level) {
    if (!needsSwitch(player, level)) {
      return;
    }
    var path = new Throwable("entry path");
    var frames = path.getStackTrace();
    if (this.reportedPaths.add(frames.length > 4 ? frames[4].toString() : "")) {
      McWorldMod.LOGGER.warn("{} entered {} with the profile of group {} outside a group switch; switching",
          player.getPlainTextName(), level.dimension().identifier(), GroupProfiles.groupOf(player), path);
    }
    this.requeue(player, level);
  }

  public void resync(ServerLevel level) {
    for (ServerPlayer player : List.copyOf(level.players())) {
      if (needsSwitch(player, level)) {
        this.requeue(player, level);
      }
    }
  }

  private void requeue(ServerPlayer player, ServerLevel level) {
    this.pending.putIfAbsent(player, new TeleportTransition(level, player.position(), Vec3.ZERO, player.getYRot(),
        player.getXRot(), TeleportTransition.DO_NOTHING));
  }

  public void scheduleProfileDeletion(Identifier group) {
    this.deletedGroups.add(group);
  }

  public void deleteTemporaryProfiles() {
    this.profiles.deleteTemporaryGroups();
  }

  public boolean isDeleting(Identifier group) {
    return this.deletedGroups.contains(group);
  }

  public void clearProfiles(Identifier group) {
    this.profiles.deleteGroup(group);
  }

  public void drain() {
    if (this.pending.isEmpty() && this.deletedGroups.isEmpty()) {
      return;
    }
    var batch = List.copyOf(this.pending.entrySet());
    this.pending.clear();
    for (var entry : batch) {
      if (entry.getKey().isDeadOrDying()) {
        continue;
      }
      var player = entry.getKey().connection.player;
      this.leaving.remove(player.getUUID());
      if (player.isRemoved()
          || entry.getValue().newLevel() instanceof CustomServerLevel target && !target.canTeleport()) {
        continue;
      }
      if (!needsSwitch(player, entry.getValue().newLevel())) {
        player.teleport(entry.getValue());
        continue;
      }
      var oldLevel = player.level();
      GroupSwitch.settle(player);
      var switched = this.switcher.run(player, player, entry.getValue(), Entity.RemovalReason.CHANGED_DIMENSION);
      if (switched.level() != oldLevel) {
        ((ServerPlayerAccessor) switched).mcworlds$triggerDimensionChangeTriggers(oldLevel);
      }
    }
    List.copyOf(this.deletedGroups).forEach(this::deleteUnusedProfiles);
  }

  private void deleteUnusedProfiles(Identifier group) {
    var inUse = this.server.getPlayerList().getPlayers().stream()
        .anyMatch(player -> group.equals(GroupProfiles.markerOf(player)));
    if (!inUse) {
      this.deletedGroups.remove(group);
      this.profiles.deleteGroup(group);
      McWorld.GROUP_DELETED.invoker().groupDeleted(this.server, group);
    }
  }

  public @Nullable ServerPlayer respawn(ServerPlayer player, boolean keepAll, Entity.RemovalReason reason) {
    var level = player.level();
    var group = GroupProfiles.groupOf(player);
    var goHome = (this.leaving.remove(player.getUUID()) || this.isClosed(group)) && TemporaryIds.isTemporary(group);
    var info = goHome ? this.homeTransition(player)
        : player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
    if (!needsSwitch(player, info.newLevel())) {
      return null;
    }
    GroupSwitch.settle(player);
    var source = player;
    if (!keepAll) {
      var deathState = new ServerPlayer(this.server, level, player.getGameProfile(), player.clientInformation());
      deathState.connection = player.connection;
      deathState.restoreFrom(player, false);
      AttachmentTargetImpl.transfer(player, deathState, true);
      deathState.snapTo(player.position(), player.getYRot(), player.getXRot());
      if (!info.missingRespawnBlock()) {
        deathState.copyRespawnPosition(player);
      }
      player.entityTags().forEach(deathState::addTag);
      player.getPostEffects().forEach(deathState::addPostEffect);
      source = deathState;
    }
    return this.switcher.run(player, source, info, reason);
  }

  public boolean save(ServerPlayer player) {
    var group = GroupProfiles.groupOf(player);
    var inDefault = WorldGroups.isDefault(group);
    if (inDefault && this.groups.all().isEmpty()) {
      this.profiles.deleteRecord(player.getUUID());
      return false;
    }
    var tag = this.profiles.tagOf(player);
    this.profiles.writeRecord(player, group, tag);
    if (inDefault) {
      return false;
    }
    this.profiles.write(group, player.getUUID(), tag);
    return true;
  }

  public void prepareStats(Player player) {
    var stats = ((PlayerListAccessor) this.server.getPlayerList()).mcworlds$stats();
    var group = WorldGroups.groupOrDefault(player.level());
    if (!stats.containsKey(player.getUUID()) && !WorldGroups.isDefault(group)) {
      stats.put(player.getUUID(), new ServerStatsCounter(this.server, this.profiles.statsFile(group, player.getUUID())));
    }
  }

  public Path advancementsFile(ServerPlayer player, Path vanillaFile) {
    var group = WorldGroups.groupOrDefault(player.level());
    return WorldGroups.isDefault(group) ? vanillaFile : this.profiles.advancementsFile(group, player.getUUID());
  }

  public Optional<CompoundTag> resolveLogin(NameAndId nameAndId, Optional<CompoundTag> live) {
    var record = this.profiles.record(this.profiles.storageId(nameAndId));
    var where = record.or(() -> live);
    var savedDimension = where.map(GroupProfiles::dimensionOf).orElse(null);
    var level = savedDimension != null ? this.liveLevel(savedDimension) : null;
    if (level == null) {
      var home = record.flatMap(this.profiles::validHome);
      var homeLevel = home.map(GroupProfiles::dimensionOf).map(this::liveLevel).orElse(null);
      if (homeLevel != null) {
        McWorldMod.LOGGER.info("{} logged out in missing dimension {}, joining their home in {}", nameAndId.name(),
            savedDimension, homeLevel.dimension().identifier());
        var homeProfile = this.profiles.load(WorldGroups.groupOrDefault(homeLevel), nameAndId);
        return Optional.of(GroupProfiles.withPosition(homeProfile.orElseGet(CompoundTag::new), home.orElseThrow()));
      }
    }
    var target = WorldGroups.groupOrDefault(level != null ? level : this.server.findRespawnDimension());
    var profile = this.profiles.load(target, nameAndId);
    if (level == null) {
      if (savedDimension != null) {
        McWorldMod.LOGGER.info("{} logged out in missing dimension {}, joining {} at the server spawn", nameAndId.name(),
            savedDimension.identifier(), target);
      }
      profile.ifPresent(GroupProfiles::stripPosition);
      return profile;
    }
    var saved = record.map(GroupProfiles::groupOf).orElse(null);
    if (saved != null && !saved.equals(target)) {
      McWorldMod.LOGGER.info("{} logged out in group {}, {} is now in {}", nameAndId.name(), saved,
          savedDimension.identifier(), target);
    }
    return Optional.of(GroupProfiles.withPosition(profile.orElseGet(CompoundTag::new), where.orElseThrow()));
  }
}
