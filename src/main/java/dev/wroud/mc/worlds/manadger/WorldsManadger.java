package dev.wroud.mc.worlds.manadger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;

import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.server.CustomServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.TicketStorage;

public class WorldsManadger {
  private static final Logger LOGGER = LogUtils.getLogger();
  private Map<ResourceLocation, WorldHandle> worlds = new HashMap<>();
  private MinecraftServer server;
  private ChunkProgressListener chunkProgressListener;
  private WorldsData worldsData;

  public WorldsManadger(MinecraftServer server) {
    this.server = server;
    this.chunkProgressListener = ((MinecraftServerAccessor) server).getProgressListenerFactory()
        .create(this.server.getWorldData().getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));
    this.worldsData = server.overworld().getDataStorage().computeIfAbsent(WorldsData.TYPE);
  }

  public Collection<ResourceLocation> getWorldIds() {
    return this.worlds.keySet();
  }

  public WorldHandle getWorld(ResourceLocation location) {
    return worlds.get(location);
  }

  public void loadSavedWorlds() {
    worldsData.getLevelsData().forEach((location, levelData) -> {
      loadOrCreateWorld(location, levelData);
    });
  }

  public void prepareSavedWorlds() {
    worlds.values().forEach(handle -> prepareWorld(handle));
  }

  public WorldHandle loadOrCreateWorld(ResourceLocation id, LevelData levelData) {
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
    var serverLevel = new CustomServerLevel(
        this.server,
        ((MinecraftServerAccessor) this.server).getExecutor(),
        ((MinecraftServerAccessor) this.server).getStorageSource(),
        levelData,
        resourceKey,
        levelData.getLevelStem(),
        chunkProgressListener,
        levelData.isDebugWorld(),
        ImmutableList.of(),
        this.server.overworld().getRandomSequences());

    var worldHandle = new WorldHandle(id, levelData, serverLevel);
    worlds.put(id, worldHandle);
    this.worldsData.addLevelData(id, levelData);
    return worldHandle;
  }

  public void prepareWorld(WorldHandle handle) {
    TicketStorage ticketStorage = handle.getServerLevel().getDataStorage().get(TicketStorage.TYPE);
    if (ticketStorage != null) {
      LOGGER.info("Activating all deactivated tickets for world: {}", handle.getId());
      ticketStorage.activateAllDeactivatedTickets();
    }
    handle.getServerLevel().setSpawnSettings(this.server.isSpawningMonsters());
    LOGGER.info("Spawn settings updated for world: {}", handle.getId());
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
}
