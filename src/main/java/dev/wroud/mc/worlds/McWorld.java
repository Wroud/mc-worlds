package dev.wroud.mc.worlds;

import java.util.Collection;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.manager.LazyLevelMap;
import dev.wroud.mc.worlds.manager.ServerLevelProvider;
import dev.wroud.mc.worlds.manager.WorldSpawns;
import dev.wroud.mc.worlds.manager.WorldsCreator;
import dev.wroud.mc.worlds.manager.WorldsException;
import dev.wroud.mc.worlds.manager.WorldsManager;
import dev.wroud.mc.worlds.manager.group.WorldGroup;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.manager.group.WorldKind;
import dev.wroud.mc.worlds.manager.group.transition.GroupTransitions;
import dev.wroud.mc.worlds.manager.importer.ImportResult;
import dev.wroud.mc.worlds.manager.importer.WorldImporter;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData.RespawnData;

public class McWorld {
  public static final Identifier DEFAULT_GROUP = WorldGroups.DEFAULT;

  public static final Event<AfterSwitch> AFTER_SWITCH = EventFactory.createArrayBacked(AfterSwitch.class,
      (oldPlayer, newPlayer, from, to) -> {
      }, listeners -> (oldPlayer, newPlayer, from, to) -> {
        for (var listener : listeners) {
          listener.afterSwitch(oldPlayer, newPlayer, from, to);
        }
      });

  public static final Event<GroupDeleted> GROUP_DELETED = EventFactory.createArrayBacked(GroupDeleted.class,
      (server, group) -> {
      }, listeners -> (server, group) -> {
        for (var listener : listeners) {
          listener.groupDeleted(server, group);
        }
      });

  private static final String UNKNOWN_WORLD = "dev.wroud.mc.worlds.command.exception.unknown_world";
  private static final String GROUP_KEY = "dev.wroud.mc.worlds.command.group.";

  private final MinecraftServer server;
  private final WorldsManager worldsManager;

  McWorld(MinecraftServer server) {
    this.server = server;
    this.worldsManager = new WorldsManager(server);
  }

  public static McWorld of(MinecraftServer server) {
    var worlds = McWorldMod.instance(server);
    if (worlds == null) {
      throw new IllegalStateException("Worlds has not started on this server yet");
    }
    return worlds;
  }

  WorldsManager manager() {
    return this.worldsManager;
  }

  public CompletableFuture<ServerLevel> createWorld(Identifier id, LevelStem stem, @Nullable Long seed) {
    return WorldsCreator.createWorld(this.server, id, stem, seed);
  }

  public CompletableFuture<ServerLevel> createWorld(Identifier id, ResourceKey<ServerLevelProvider<?>> provider,
      @Nullable Long seed) {
    return WorldsCreator.createWorld(this.server, id, provider, seed);
  }

  public static CompletableFuture<ServerLevel> whenReady(ServerLevel level) {
    return level instanceof CustomServerLevel custom ? custom.whenReady() : CompletableFuture.completedFuture(level);
  }

  public LevelStem levelStem(@Nullable ResourceKey<WorldPreset> preset, @Nullable Identifier dimension) {
    return WorldsCreator.levelStem(this.server, preset, dimension);
  }

  public LevelStem levelStem(ResourceKey<LevelStem> key) {
    return WorldsCreator.levelStem(this.server, key);
  }

  public CompletableFuture<ImportResult> importWorld(Identifier id, String source, @Nullable Identifier dimension,
      boolean all, @Nullable Identifier group) {
    return this.importWorld(id, source, dimension, all, group, targets -> {
    });
  }

  public CompletableFuture<ImportResult> importWorld(Identifier id, String source, @Nullable Identifier dimension,
      boolean all, @Nullable Identifier group, Consumer<Collection<Identifier>> started) {
    return WorldImporter.run(this.server, id, source, dimension, all, group, started);
  }

  public void deleteWorld(Identifier id) {
    if (!this.worldsManager.deleteWorld(id)) {
      throw new WorldsException(UNKNOWN_WORLD);
    }
  }

  public Set<Identifier> worlds() {
    return this.worldsManager.allWorldIds();
  }

  public boolean isLoaded(Identifier world) {
    return LazyLevelMap.loaded(this.server).containsKey(ResourceKey.create(Registries.DIMENSION, world));
  }

  public boolean loadsOnStartup(Identifier world) {
    return !this.levelData(world).isLazy();
  }

  public void setLoadOnStartup(Identifier world, boolean load) {
    this.levelData(world).setLazy(!load);
  }

  public @Nullable RespawnData worldSpawn(Identifier world) {
    return this.levelData(world).getRespawnData();
  }

  public void setWorldSpawn(Identifier world, RespawnData spawn) {
    var data = this.levelData(world);
    if (!spawn.dimension().identifier().equals(world)) {
      throw new WorldsException("dev.wroud.mc.worlds.command.settings.spawn.exception.outside", world);
    }
    data.setSpawn(spawn);
    this.service().worldSpawnChanged(world);
  }

  public boolean teleport(ServerPlayer player, Identifier world) {
    var level = this.server.getLevel(ResourceKey.create(Registries.DIMENSION, world));
    if (level == null) {
      throw new WorldsException(UNKNOWN_WORLD);
    }
    if (player.level() == level || level instanceof CustomServerLevel custom && !custom.canTeleport()) {
      return false;
    }
    player.setPortalCooldown();
    player.teleport(WorldSpawns.transition(level, player));
    return true;
  }

  public static TeleportTransition spawnTransition(ServerLevel level, Entity entity) {
    return WorldSpawns.transition(level, entity);
  }

  public void createGroup(Identifier group) {
    this.refuseDefault(group);
    if (!FileUtil.isValidPathSegment(group.getNamespace()) || !FileUtil.isValidPathSegment(group.getPath())) {
      throw new WorldsException(GROUP_KEY + "create.exception.invalid", group);
    }
    if (this.service().transitions().isDeleting(group)) {
      throw new WorldsException(GROUP_KEY + "create.exception.deleting", group);
    }
    if (!this.service().create(group)) {
      throw new WorldsException(GROUP_KEY + "create.exception.exists", group);
    }
  }

  public WorldGroup deleteGroup(Identifier group) {
    this.refuseDefault(group);
    var removed = this.service().delete(group);
    if (removed == null) {
      throw unknownGroup(group);
    }
    return removed;
  }

  public WorldGroup.Join joinGroup(Identifier world, Identifier group, boolean asMain) {
    this.requireGroupable(world);
    this.refuseDefault(group);
    var groups = this.service();
    if (groups.get(group) != null && !WorldGroups.accepts(group, world)) {
      throw new WorldsException(GROUP_KEY + "join.exception.not_temporary", world, group);
    }
    if (asMain && !WorldKind.of(this.server, world).isMain()) {
      throw new WorldsException(GROUP_KEY + "join.exception.other", world);
    }
    var joined = groups.join(world, group, asMain);
    if (joined == null) {
      throw unknownGroup(group);
    }
    return joined;
  }

  public Identifier leaveGroup(Identifier world) {
    this.requireGroupable(world);
    var previous = this.service().leave(world);
    if (previous == null) {
      throw new WorldsException(GROUP_KEY + "leave.exception.default", world);
    }
    return previous;
  }

  public void setGroupSpawn(Identifier group, @Nullable RespawnData spawn) {
    if (WorldGroups.isDefault(group)) {
      throw new WorldsException(GROUP_KEY + "spawn.exception.default");
    }
    var target = this.service().get(group);
    if (target == null) {
      throw unknownGroup(group);
    }
    if (spawn != null && !target.contains(spawn.dimension().identifier())) {
      throw new WorldsException(GROUP_KEY + "spawn.exception.outside", group);
    }
    this.service().setSpawn(group, spawn);
  }

  public WorldGroup.Spawn groupSpawn(Identifier group) {
    return this.service().spawnOf(group);
  }

  public Identifier groupOf(Identifier world) {
    return this.service().groupOrDefault(world);
  }

  public @Nullable WorldGroup group(Identifier group) {
    return this.service().get(group);
  }

  public SequencedCollection<Identifier> members(Identifier group) {
    return this.service().members(group);
  }

  public Map<Identifier, WorldGroup> groups() {
    return this.service().all();
  }

  public static Identifier temporaryId(String name) {
    return TemporaryIds.generate(name);
  }

  public static boolean isTemporary(Identifier id) {
    return TemporaryIds.isTemporary(id);
  }

  public boolean returnHome(ServerPlayer player) {
    return this.service().transitions().returnHome(player);
  }

  public static Identifier groupOf(Level level) {
    return WorldGroups.groupOrDefault(level);
  }

  public static Identifier groupOf(ServerPlayer player) {
    return GroupTransitions.groupOf(player);
  }

  @FunctionalInterface
  public interface AfterSwitch {
    void afterSwitch(ServerPlayer oldPlayer, ServerPlayer newPlayer, Identifier from, Identifier to);
  }

  @FunctionalInterface
  public interface GroupDeleted {
    void groupDeleted(MinecraftServer server, Identifier group);
  }

  private WorldGroups service() {
    return this.worldsManager.getGroups();
  }

  private WorldsLevelData levelData(Identifier world) {
    var data = this.worldsManager.getWorldsData().getLevelData(world);
    if (data == null) {
      throw new WorldsException(UNKNOWN_WORLD);
    }
    return data;
  }

  private void requireGroupable(Identifier world) {
    if (this.worldsManager.getWorldsData().getLevelData(world) != null) {
      return;
    }
    if (this.server.levelKeys().contains(ResourceKey.create(Registries.DIMENSION, world))) {
      throw new WorldsException(GROUP_KEY + "exception.vanilla");
    }
    throw new WorldsException(UNKNOWN_WORLD);
  }

  private void refuseDefault(Identifier group) {
    if (WorldGroups.isDefault(group)) {
      throw new WorldsException(GROUP_KEY + "exception.default");
    }
  }

  private static WorldsException unknownGroup(Identifier group) {
    return new WorldsException(GROUP_KEY + "exception.unknown_group", group);
  }
}
