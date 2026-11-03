package dev.wroud.mc.worlds.manager.group.transition;

import java.util.Optional;

import dev.wroud.mc.worlds.McWorld;
import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.mixin.groups.PlayerListAccessor;
import dev.wroud.mc.worlds.mixin.groups.ServerPlayerAccessor;
import dev.wroud.mc.worlds.mixin.groups.StatsCounterAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;

final class GroupSwitch {
  private final GroupProfiles profiles;

  GroupSwitch(GroupProfiles profiles) {
    this.profiles = profiles;
  }

  static void settle(ServerPlayer player) {
    if (player.isSleeping()) {
      player.stopSleepInBed(true, true);
    }
    player.stopUsingItem();
    player.closeContainer();
    player.removeVehicle();
    player.ejectPassengers();
  }

  ServerPlayer run(ServerPlayer old, ServerPlayer source, TeleportTransition target,
      Entity.RemovalReason reason) {
    var level = target.newLevel();
    var server = level.getServer();
    var playerList = server.getPlayerList();
    var uuid = old.getUUID();
    var from = GroupProfiles.groupOf(old);
    var to = WorldGroups.groupOrDefault(level);
    var move = PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(old), PositionMoveRotation.of(target),
        target.relatives());

    source.setAttached(GroupProfiles.MARKER, from);
    ((PlayerListAccessor) playerList).mcworlds$save(source);
    var enteredByDeath = source != old && !TemporaryIds.isTemporary(from) && TemporaryIds.isTemporary(to);
    if (enteredByDeath) {
      this.profiles.deleteRecord(uuid);
    }
    old.getEnderPearls().forEach(pearl -> pearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER));

    boolean listed = playerList.getPlayers().remove(old);
    old.level().removePlayerImmediately(old, reason);

    old.getAdvancements().clearTriggers();
    ((PlayerListAccessor) playerList).mcworlds$stats().remove(uuid);
    ((PlayerListAccessor) playerList).mcworlds$advancements().remove(uuid);

    var player = new ServerPlayer(server, level, old.getGameProfile(), old.clientInformation());
    player.connection = old.connection;
    player.setId(old.getId());
    player.setMainArm(old.getMainArm());
    var chatSession = old.getChatSession();
    if (chatSession != null) {
      player.setChatSession(chatSession);
    }
    player.requestDebugSubscriptions(((ServerPlayerAccessor) old).mcworlds$getRequestedDebugSubscriptions());

    Optional<CompoundTag> profile = this.profiles.load(to, old.nameAndId());
    try (var reporter = new ProblemReporter.ScopedCollector(player.problemPath(), McWorldMod.LOGGER)) {
      Optional<ValueInput> input = profile.map(tag -> {
        GroupProfiles.stripPosition(tag);
        return TagValueInput.create(reporter, server.registryAccess(), tag);
      });
      input.ifPresent(player::load);
      player.setAttached(GroupProfiles.MARKER, to);
      player.snapTo(move.position(), move.yRot(), move.xRot());
      player.setDeltaMovement(move.deltaMovement());
      player.setPortalCooldown();

      var levelData = level.getLevelData();
      if (target.missingRespawnBlock()) {
        player.connection
            .send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
      }
      player.connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(level), (byte) 0));
      player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
      player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(level.getRespawnData()));
      player.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
      player.connection.send(
          new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
      playerList.sendActivePlayerEffects(player);
      player.sendPostEffects();
      playerList.sendLevelInfo(player, level);
      playerList.sendPlayerPermissionLevel(player);
      level.addRespawnedPlayer(player);
      if (listed) {
        playerList.getPlayers().add(player);
        ((PlayerListAccessor) playerList).mcworlds$playersByUUID().put(uuid, player);
      }
      player.initInventoryMenu();
      player.setHealth(player.getHealth());
      player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
      player.getStats().markAllDirty();
      Object2IntMap<Stat<?>> cleared = new Object2IntOpenHashMap<>();
      for (var stat : ((StatsCounterAccessor) old.getStats()).mcworlds$stats().keySet()) {
        if (player.getStats().getValue(stat) == 0) {
          cleared.put(stat, 0);
        }
      }
      if (!cleared.isEmpty()) {
        player.connection.send(new ClientboundAwardStatsPacket(cleared));
      }
      player.getRecipeBook().sendInitialRecipeBook(player);
      playerList.sendAllPlayerInfo(player);
      input.ifPresent(player::loadAndSpawnEnderPearls);
    }
    player.connection.player = player;
    player.connection.resetPosition();

    ((PlayerListAccessor) playerList).mcworlds$save(player);
    McWorld.AFTER_SWITCH.invoker().afterSwitch(old, player, from, to);
    target.postTeleportTransition().onTransition(player);
    McWorldMod.LOGGER.info("{} switched from group {} to {} in {}", player.getPlainTextName(), from, to,
        level.dimension().identifier());
    return player;
  }
}
