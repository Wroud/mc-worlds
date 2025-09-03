package dev.wroud.mc.worlds.manadger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
import net.minecraft.world.level.border.BorderChangeListener.DelegateBorderChangeListener;

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

  public void loadWorlds() {
    worldsData.getLevelsData().forEach((location, levelData) -> {
      loadWorld(location, levelData);
    });
  }

  public void prepareWorlds() {
    worlds.values().forEach(handle -> prepareWorld(handle));
  }

  public WorldHandle loadWorld(ResourceLocation id, LevelData levelData) {
    if (worlds.containsKey(id)) {
      return worlds.get(id);
    }
    LOGGER.info("Loading world: {}", id);

    var resourceKey = ResourceKey.create(Registries.DIMENSION, id);
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

    this.server.overworld().getWorldBorder()
        .addListener(new DelegateBorderChangeListener(serverLevel.getWorldBorder()));
    ((MinecraftServerAccessor) this.server).getLevels().put(resourceKey, serverLevel);

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
    LOGGER.info("Waiting for next tick for world: {}", handle.getId());
    // ((MinecraftServerAccessor) this.server).waitUntilNextTick();
    LOGGER.info("Next tick complete for world: {}", handle.getId());
    handle.getServerLevel().setSpawnSettings(this.server.isSpawningMonsters());
    LOGGER.info("Spawn settings updated for world: {}", handle.getId());
  }

  public boolean removeWorld(ResourceLocation location) {
    var handle = worlds.remove(location);
    if (handle != null) {
      ((MinecraftServerAccessor) this.server).getLevels().remove(handle.getServerLevel().dimension());
      return true;
    }
    return false;
  }
}
