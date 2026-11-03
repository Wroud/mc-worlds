package dev.wroud.mc.worlds.manager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.core.registries.WorldsRegistries;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.tags.DimensionTypeTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;

public class WorldsManager {
  private Map<Identifier, WorldHandle> worlds = new HashMap<>();
  private MinecraftServer server;
  private WorldsData worldsData;
  private final WorldGroups groups;
  private final TemporaryStorage temporaryStorage;
  private final Set<Identifier> unreadableGenerators = new HashSet<>();
  private final AtomicReference<@Nullable RunningImport> runningImport = new AtomicReference<>();

  public WorldsManager(MinecraftServer server) {
    this.server = server;
    this.worldsData = server.getDataStorage().computeIfAbsent(WorldsData.TYPE);
    this.groups = new WorldGroups(server, this);
    this.temporaryStorage = new TemporaryStorage(server);
  }

  public TemporaryStorage getTemporaryStorage() {
    return temporaryStorage;
  }

  public WorldGroups getGroups() {
    return groups;
  }

  public synchronized Collection<Identifier> getWorldIds() {
    return this.worlds.keySet();
  }

  public Set<Identifier> allWorldIds() {
    var ids = new TreeSet<Identifier>(this.worldsData.getLevelsData().keySet());
    this.server.levelKeys().forEach(key -> ids.add(key.identifier()));
    return ids;
  }

  public synchronized WorldHandle getWorld(Identifier location) {
    return worlds.get(location);
  }

  public WorldsData getWorldsData() {
    return worldsData;
  }

  public boolean tryBeginImport(String description) {
    return this.runningImport.compareAndSet(null, new RunningImport(description, "starting"));
  }

  public void setImportPhase(String phase) {
    this.runningImport.updateAndGet(current -> current == null ? null : new RunningImport(current.description(), phase));
  }

  public void endImport() {
    this.runningImport.set(null);
  }

  public Optional<String> currentImport() {
    return Optional.ofNullable(this.runningImport.get())
        .map(current -> current.description() + " (" + current.phase() + ")");
  }

  public void loadSavedWorlds() {
    sweepTemporary();
    worldsData.getLevelsData().forEach((location, levelData) -> {
      if (levelData.isLazy()) {
        return;
      }
      loadOrCreateWorld(location, levelData);
    });
  }

  private void sweepTemporary() {
    var session = ((MinecraftServerAccessor) this.server).getStorageSource();
    deleteDirectory(session.getDimensionPath(ResourceKey.create(Registries.DIMENSION,
        Identifier.fromNamespaceAndPath(TemporaryIds.NAMESPACE, "sweep"))).getParent());
    this.groups.sweepTemporary();
  }

  public synchronized @Nullable WorldHandle loadOrCreateWorld(Identifier id, WorldsLevelData levelData) {
    var loaded = worlds.get(id);
    if (loaded != null) {
      return loaded;
    }

    if (unreadableGenerators.contains(id)) {
      return null;
    }

    if (!this.server.isSameThread()) {
      this.server.execute(() -> {
        if (worldsData.getLevelData(id) != null) {
          loadOrCreateWorld(id, levelData);
        }
      });
      return null;
    }

    var resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    var serverLevelProvider = this.provider(levelData);
    var levelStem = this.levelStem(id, levelData);

    if (levelStem == null) {
      unreadableGenerators.add(id);
      McWorldMod.LOGGER.error(
          "Skipping world {}: its stored generator could not be read and provider {} cannot rebuild one",
          id, levelData.getProvider().identifier());
      return null;
    }

    var session = ((MinecraftServerAccessor) this.server).getStorageSource();
    File worldDirectory = session.getDimensionPath(resourceKey).toFile();
    if (worldDirectory.exists()) {
      McWorldMod.LOGGER.info("Loading world: {}", id);
    } else {
      McWorldMod.LOGGER.info("Creating new world: {}", id);
    }

    levelData.setWorldData(server.getWorldData());

    List<CustomSpawner> list = ImmutableList.of();

    if (levelStem.type().is(DimensionTypeTags.OVERWORLD_LIKE)) {
      list = ImmutableList.of(
          new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(),
          new WanderingTraderSpawner(null));
    }

    var serverLevel = serverLevelProvider.value().create(
        this.server,
        ((MinecraftServerAccessor) this.server).getExecutor(),
        ((MinecraftServerAccessor) this.server).getStorageSource(),
        levelData,
        resourceKey,
        levelStem,
        list);

    var worldHandle = new WorldHandle(id, levelData, serverLevel);
    worlds.put(id, worldHandle);
    this.worldsData.addLevelData(id, levelData);
    return worldHandle;
  }

  public void forgetUnreadable(Identifier id) {
    this.unreadableGenerators.remove(id);
  }

  public @Nullable Holder<DimensionType> dimensionType(Identifier id) {
    var levelData = this.worldsData.getLevelData(id);
    var levelStem = levelData != null ? this.levelStem(id, levelData) : null;
    return levelStem != null ? levelStem.type() : null;
  }

  private @Nullable LevelStem levelStem(Identifier id, WorldsLevelData levelData) {
    var stored = levelData.getLevelStem();
    return stored != null ? stored
        : this.provider(levelData).value().createLevelStem(this.server, ResourceKey.create(Registries.DIMENSION, id),
            levelData.getSeed());
  }

  private Holder.Reference<ServerLevelProvider<?>> provider(WorldsLevelData levelData) {
    return this.server.registryAccess().lookupOrThrow(WorldsRegistries.LEVEL_PROVIDER)
        .getOrThrow(levelData.getProvider());
  }

  public synchronized void unloadWorld(Identifier location) {
    var handle = worlds.remove(location);
    if (handle != null) {
      var world = handle.getServerLevel();

      if (world.isDeleteOnClose()) {
        if (world.isStopped() && world.isClosed()) {
          deleteDirectory(((MinecraftServerAccessor) this.server).getStorageSource()
              .getDimensionPath(world.dimension()));
        }
        this.worldsData.removeLevelData(location);
        this.groups.leave(location);
      }
      world.failReady();
    }
  }

  public boolean deleteWorld(Identifier id) {
    var handle = getWorld(id);
    if (handle != null) {
      handle.getServerLevel().stop(true);
      this.groups.leave(id);
      return true;
    }
    if (worldsData.getLevelData(id) == null) {
      return false;
    }
    this.unreadableGenerators.remove(id);
    this.temporaryStorage.forget(id);
    this.groups.leave(id);
    this.worldsData.removeLevelData(id);
    deleteDirectory(((MinecraftServerAccessor) this.server).getStorageSource()
        .getDimensionPath(ResourceKey.create(Registries.DIMENSION, id)));
    return true;
  }

  private record RunningImport(String description, String phase) {
  }

  private static void deleteDirectory(Path path) {
    File directory = path.toFile();
    if (directory.exists()) {
      try {
        FileUtils.deleteDirectory(directory);
      } catch (IOException e) {
        McWorldMod.LOGGER.warn("Failed to delete directory {}", directory, e);
        try {
          FileUtils.forceDeleteOnExit(directory);
        } catch (IOException ignored) {
        }
      }
    }
  }
}
