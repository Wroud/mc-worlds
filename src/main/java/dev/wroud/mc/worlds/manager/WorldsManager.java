package dev.wroud.mc.worlds.manager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;

import dev.wroud.mc.worlds.abstractions.MinecraftServerAbstraction;
import dev.wroud.mc.worlds.core.registries.WorldsRegistries;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.tags.DimensionTypeTags;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.storage.LevelData;

public class WorldsManager {
  public static final Logger LOGGER = LogUtils.getLogger();
  private Map<ResourceLocation, WorldHandle> worlds = new HashMap<>();
  private MinecraftServer server;
  private WorldsData worldsData;

  public WorldsManager(MinecraftServer server) {
    this.server = server;
    this.worldsData = server.overworld().getDataStorage().computeIfAbsent(WorldsData.TYPE);
  }

  public Collection<ResourceLocation> getWorldIds() {
    return this.worlds.keySet();
  }

  public WorldHandle getWorld(ResourceLocation location) {
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

  public WorldHandle loadOrCreateWorld(ResourceLocation id, WorldsLevelData levelData) {
    if (worlds.containsKey(id)) {
      return worlds.get(id);
    }

    var resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    var session = ((MinecraftServerAccessor) this.server).getStorageSource();
    File worldDirectory = session.getDimensionPath(resourceKey).toFile();
    if (worldDirectory.exists()) {
      LOGGER.info("Loading world: {}", id);
    } else {
      LOGGER.info("Creating new world: {}", id);
    }

    levelData.setWorldData(server.getWorldData());

    List<CustomSpawner> list = ImmutableList.of();

    if (levelData.getLevelStem().type().is(DimensionTypeTags.OVERWORLD_LIKE)) {
      list = ImmutableList.of(
          new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(),
          new WanderingTraderSpawner(levelData));
    }

    var serverLevelProvider = this.server.registryAccess()
        .lookupOrThrow(WorldsRegistries.LEVEL_PROVIDER)
        .getOrThrow(levelData.getProvider());

    var serverLevel = serverLevelProvider.value().create(
        this.server,
        ((MinecraftServerAccessor) this.server).getExecutor(),
        ((MinecraftServerAccessor) this.server).getStorageSource(),
        levelData,
        resourceKey,
        levelData.getLevelStem(),
        list,
        null);

    var worldHandle = new WorldHandle(id, levelData, serverLevel);
    worlds.put(id, worldHandle);
    this.worldsData.addLevelData(id, levelData);

    this.initializeWorld(levelData, serverLevel);
    return worldHandle;
  }

  public void unloadWorld(ResourceLocation location) {
    var handle = worlds.remove(location);
    if (handle != null) {
      var world = handle.getServerLevel();

      if (world.noSave) {
        if (world.isManuallyStopped()) {
          var session = ((MinecraftServerAccessor) this.server).getStorageSource();
          File worldDirectory = session.getDimensionPath(world.dimension()).toFile();
          if (worldDirectory.exists()) {
            try {
              FileUtils.deleteDirectory(worldDirectory);
            } catch (IOException e) {
              LOGGER.warn("Failed to delete world directory for dimension {}", world.dimension(), e);
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

  private void initializeWorld(LevelData levelData, CustomServerLevel level) {
    if (!DimensionDetectionUtil.isOverworldLikeDimension(level)) {
      return;
    }
    MinecraftServerAbstraction.initializeLevel(server, level);
  }
}
