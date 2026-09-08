package dev.wroud.mc.worlds.manager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.core.registries.WorldsRegistries;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.tags.DimensionTypeTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;

public class WorldsManager {
  private Map<Identifier, WorldHandle> worlds = new HashMap<>();
  private MinecraftServer server;
  private WorldsData worldsData;
  private final Set<Identifier> unreadableGenerators = new HashSet<>();

  public WorldsManager(MinecraftServer server) {
    this.server = server;
    this.worldsData = server.getDataStorage().computeIfAbsent(WorldsData.TYPE);
  }

  public synchronized Collection<Identifier> getWorldIds() {
    return this.worlds.keySet();
  }

  public synchronized WorldHandle getWorld(Identifier location) {
    return worlds.get(location);
  }

  public WorldsData getWorldsData() {
    return worldsData;
  }

  public void loadSavedWorlds() {
    worldsData.getLevelsData().forEach((location, levelData) -> {
      if (levelData.isLazy()) {
        return;
      }
      loadOrCreateWorld(location, levelData);
    });
  }

  public synchronized @Nullable WorldHandle loadOrCreateWorld(Identifier id, WorldsLevelData levelData) {
    var loaded = worlds.get(id);
    if (loaded != null) {
      return loaded;
    }

    if (unreadableGenerators.contains(id)) {
      return null;
    }

    var resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    var serverLevelProvider = this.server.registryAccess()
        .lookupOrThrow(WorldsRegistries.LEVEL_PROVIDER)
        .getOrThrow(levelData.getProvider());

    var storedStem = levelData.getLevelStem();
    var levelStem = storedStem != null ? storedStem
        : serverLevelProvider.value().createLevelStem(this.server, resourceKey, levelData.getSeed());

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

  public synchronized void unloadWorld(Identifier location) {
    var handle = worlds.remove(location);
    if (handle != null) {
      var world = handle.getServerLevel();

      if (world.isDeleteOnClose()) {
        if (world.isStopped() && world.isClosed()) {
          var session = ((MinecraftServerAccessor) this.server).getStorageSource();
          File worldDirectory = session.getDimensionPath(world.dimension()).toFile();
          if (worldDirectory.exists()) {
            try {
              FileUtils.deleteDirectory(worldDirectory);
            } catch (IOException e) {
              McWorldMod.LOGGER.warn("Failed to delete world directory for dimension {}", world.dimension(), e);
              try {
                FileUtils.forceDeleteOnExit(worldDirectory);
              } catch (IOException ignored) {
              }
            }
          }
        }
        this.worldsData.removeLevelData(location);
      }
    }
  }
}
