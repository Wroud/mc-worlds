package dev.wroud.mc.worlds.manager.group;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.TreeSet;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.WorldsManager;
import dev.wroud.mc.worlds.manager.LazyLevelMap;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.manager.group.transition.GroupTransitions;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.level.storage.LevelResource;

public final class WorldGroups {
  public static final Identifier DEFAULT = Identifier.withDefaultNamespace("default");

  private final MinecraftServer server;
  private final WorldsManager manager;
  private final WorldGroupsData data;
  private final Map<Identifier, SavedDataType<GameRuleMap>> ruleTypes = new HashMap<>();
  private final Map<Identifier, GameRuleMap> temporaryRules = new HashMap<>();
  private final GroupTransitions transitions;

  public WorldGroups(MinecraftServer server, WorldsManager manager) {
    this.server = server;
    this.manager = manager;
    this.data = server.getDataStorage().computeIfAbsent(WorldGroupsData.TYPE);
    if (this.data.groups().isEmpty()) {
      this.data.setDirty(false);
    }
    this.transitions = new GroupTransitions(server, this);
  }

  public GroupTransitions transitions() {
    return this.transitions;
  }

  public static @Nullable WorldGroups of(MinecraftServer server) {
    return McWorldMod.getManager(server).map(WorldsManager::getGroups).orElse(null);
  }

  public static boolean isDefault(Identifier group) {
    return DEFAULT.equals(group);
  }

  public static @Nullable Identifier groupOf(Level level) {
    return level instanceof CustomServerLevel custom ? custom.getGroupId() : null;
  }

  public static Identifier groupOrDefault(Level level) {
    return Objects.requireNonNullElse(groupOf(level), DEFAULT);
  }

  public Identifier groupOrDefault(Identifier world) {
    return Objects.requireNonNullElse(this.groupOf(world), DEFAULT);
  }

  public Map<Identifier, WorldGroup> all() {
    return this.data.groups();
  }

  public @Nullable WorldGroup get(Identifier group) {
    return this.data.get(group);
  }

  public @Nullable Identifier groupOf(Identifier world) {
    for (var entry : this.data.groups().entrySet()) {
      if (entry.getValue().contains(world)) {
        return entry.getKey();
      }
    }
    return null;
  }

  public static boolean sameGroup(Level level, Level other) {
    return groupOrDefault(level).equals(groupOrDefault(other));
  }

  public boolean sameGroup(Level level, ResourceKey<Level> other) {
    return groupOrDefault(level).equals(this.groupOrDefault(other.identifier()));
  }

  public SequencedCollection<Identifier> members(Identifier group) {
    var found = this.data.get(group);
    if (found != null) {
      return found.members();
    }
    var ids = new TreeSet<>(this.manager.allWorldIds());
    this.data.groups().values().forEach(other -> ids.removeAll(other.members()));
    return ids;
  }

  public boolean create(Identifier group) {
    if (isDefault(group) || this.data.get(group) != null) {
      return false;
    }
    if (TemporaryIds.isTemporary(group)) {
      this.manager.getTemporaryStorage().putGroup(group);
    } else {
      this.transitions.clearProfiles(group);
    }
    this.data.put(group, new WorldGroup());
    return true;
  }

  public void sweepTemporary() {
    this.transitions.deleteTemporaryProfiles();
    this.data.groups().values().stream()
        .flatMap(group -> group.members().stream())
        .filter(TemporaryIds::isTemporary)
        .toList()
        .forEach(this::leave);
  }

  public @Nullable WorldGroup delete(Identifier group) {
    var removed = this.data.remove(group);
    if (removed == null) {
      return null;
    }
    if (TemporaryIds.isTemporary(group)) {
      removed.members().forEach(this.manager::deleteWorld);
      this.manager.getTemporaryStorage().forget(group);
    }
    removed.members().forEach(this::refresh);
    this.deleteRules(group);
    this.transitions.scheduleProfileDeletion(group);
    return removed;
  }

  public static boolean accepts(Identifier group, Identifier world) {
    return !TemporaryIds.isTemporary(group) || TemporaryIds.isTemporary(world);
  }

  public WorldGroup.@Nullable Join join(Identifier world, Identifier group, boolean asMain) {
    var target = this.data.get(group);
    if (target == null || !accepts(group, world)) {
      return null;
    }
    var previous = this.groupOf(world);
    if (previous != null && !previous.equals(group)) {
      this.data.get(previous).remove(world);
    }
    target.add(world);
    var kind = WorldKind.of(this.server, world);
    Identifier replaced = null;
    if (kind.isMain() && (asMain || target.main(kind) == null)) {
      replaced = target.setMain(kind, world);
    }
    this.data.setDirty();
    this.refresh(world);
    this.refreshSpawns(previous);
    this.refreshSpawns(group);
    return new WorldGroup.Join(group, kind, target.isMain(world), previous, world.equals(replaced) ? null : replaced);
  }

  public @Nullable Identifier leave(Identifier world) {
    var previous = this.groupOf(world);
    if (previous != null) {
      this.data.get(previous).remove(world);
      this.data.setDirty();
      this.refresh(world);
      this.refreshSpawns(previous);
    }
    return previous;
  }

  public void setSpawn(Identifier group, @Nullable RespawnData spawn) {
    this.data.get(group).setSpawn(spawn);
    this.data.setDirty();
    this.refreshSpawns(group);
  }

  public void worldSpawnChanged(Identifier world) {
    this.refreshSpawns(this.groupOf(world));
  }

  private void refreshSpawns(@Nullable Identifier group) {
    var target = group != null ? this.data.get(group) : null;
    if (target == null) {
      return;
    }
    for (var world : target.members()) {
      var handle = this.manager.getWorld(world);
      if (handle != null) {
        handle.getServerLevel().refreshGroupSpawn();
      }
    }
  }

  public WorldGroup.Spawn spawnOf(Identifier group) {
    var target = this.data.get(group);
    if (target != null) {
      var set = target.spawn();
      if (set != null && target.contains(set.dimension().identifier())) {
        return new WorldGroup.Spawn(set, WorldGroup.SpawnSource.SET);
      }
      var overworld = target.main(WorldKind.OVERWORLD);
      if (overworld != null) {
        return new WorldGroup.Spawn(this.worldSpawn(overworld), WorldGroup.SpawnSource.MAIN_OVERWORLD);
      }
      var members = target.members();
      if (!members.isEmpty()) {
        return new WorldGroup.Spawn(this.worldSpawn(members.getFirst()), WorldGroup.SpawnSource.FIRST_MEMBER);
      }
    }
    return new WorldGroup.Spawn(this.server.getRespawnData(), WorldGroup.SpawnSource.SERVER);
  }

  public RespawnData worldSpawn(Identifier world) {
    var key = ResourceKey.create(Registries.DIMENSION, world);
    var levelData = this.manager.getWorldsData().getLevelData(world);
    var spawn = levelData != null ? levelData.getRespawnData() : null;
    if (spawn == null || RespawnData.DEFAULT.equals(spawn) || !spawn.dimension().equals(key)) {
      return RespawnData.of(key, RespawnData.DEFAULT.pos(), 0.0F, 0.0F);
    }
    return spawn;
  }

  public @Nullable Identifier spawnHolder(Identifier world) {
    for (var entry : this.data.groups().entrySet()) {
      var spawn = entry.getValue().spawn();
      if (spawn != null && spawn.dimension().identifier().equals(world) && entry.getValue().contains(world)) {
        return entry.getKey();
      }
    }
    return null;
  }

  public static @Nullable ServerLevel portalDestination(MinecraftServer server, ServerLevel current, Entity entity,
      @Nullable WorldKind mainKind, ResourceKey<Level> key, Function<ResourceKey<Level>, @Nullable ServerLevel> loader) {
    var groups = of(server);
    var group = groupOf(current);
    if (groups == null || group == null) {
      return loader.apply(key);
    }
    var target = key;
    if (mainKind != null) {
      var main = groups.get(group).main(mainKind);
      if (main == null) {
        if (entity instanceof ServerPlayer player) {
          player.sendOverlayMessage(Component.translatable("dev.wroud.mc.worlds.portal.missing", group.toString(),
              Component.translatable("dev.wroud.mc.worlds.portal.kind." + mainKind.key())));
        }
        return null;
      }
      target = ResourceKey.create(Registries.DIMENSION, main);
    }
    if (!(entity instanceof ServerPlayer) && !LazyLevelMap.loaded(server).containsKey(target)) {
      return null;
    }
    return loader.apply(target);
  }

  public GameRuleMap rules(Identifier group) {
    if (TemporaryIds.isTemporary(group)) {
      return this.temporaryRules.computeIfAbsent(group, id -> GameRuleMap.of());
    }
    var rules = this.server.getDataStorage().computeIfAbsent(this.ruleType(group));
    if (rules.size() == 0) {
      rules.setDirty(false);
    }
    return rules;
  }

  private SavedDataType<GameRuleMap> ruleType(Identifier group) {
    return this.ruleTypes.computeIfAbsent(group,
        id -> PerWorldGameRules.type(McWorldMod.id("groups/" + id.getNamespace() + "/" + id.getPath() + "/game_rules")));
  }

  private void deleteRules(Identifier group) {
    if (TemporaryIds.isTemporary(group)) {
      this.temporaryRules.remove(group);
      return;
    }
    var type = this.ruleType(group);
    var rules = this.server.getDataStorage().computeIfAbsent(type);
    for (GameRule<?> rule : List.copyOf(rules.keySet())) {
      rules.remove(rule);
    }
    rules.setDirty(false);
    Path file = type.id().withSuffix(".dat")
        .resolveAgainst(((MinecraftServerAccessor) this.server).getStorageSource().getLevelPath(LevelResource.DATA));
    try {
      Files.deleteIfExists(file);
      Files.deleteIfExists(file.getParent());
    } catch (DirectoryNotEmptyException ignored) {
    } catch (IOException e) {
      McWorldMod.LOGGER.warn("Could not delete {}", file, e);
    }
  }

  private void refresh(Identifier world) {
    var handle = this.manager.getWorld(world);
    if (handle == null) {
      return;
    }
    var level = handle.getServerLevel();
    if (level.isDeleteOnClose()) {
      level.refreshGroupSpawn();
      return;
    }
    level.refreshGroup();
    this.transitions.resync(level);
  }
}
