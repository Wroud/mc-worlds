package dev.wroud.mc.worlds.server.level.state;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.level.storage.ServerLevelData;

public class SpawnPreparationHelper {
  private static final Logger LOGGER = LogUtils.getLogger();

  private final ServerLevel serverLevel;
  private ArrayList<ChunkPos> spawnChunksToCheck;
  private CompletableFuture<?> processingChunk;
  private boolean isFinished;

  public SpawnPreparationHelper(ServerLevel serverLevel) {
    this.serverLevel = serverLevel;
    this.spawnChunksToCheck = null;
    this.processingChunk = null;
    this.isFinished = false;
  }

  public boolean isFinished() {
    return isFinished;
  }

  public void tick() {
    if (isFinished) {
      return;
    }

    if (spawnChunksToCheck == null) {
      prepareForInitializing();
    }

    if (spawnChunksToCheck.isEmpty()) {
      finishInitialization();
      isFinished = true;
    } else {
      processSpawnChunk();
    }
  }

  private void prepareForInitializing() {
    var serverLevelData = (WorldsLevelData) serverLevel.getLevelData();
    spawnChunksToCheck = new ArrayList<>();

    if (!DimensionDetectionUtil.isOverworldLikeDimension(serverLevel) || !serverLevelData.getPrepareSpawn()) {
      return;
    }

    var worldData = serverLevel.getServer().getWorldData();
    var debug = worldData.isDebugWorld();

    if (SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD && SharedConstants.DEBUG_WORLD_RECREATE) {
      serverLevelData.setSpawn(RespawnData.of(serverLevel.dimension(), new BlockPos(0, 64, -100), 0.0F, 0.0F));
    } else if (debug) {
      serverLevelData.setSpawn(RespawnData.of(serverLevel.dimension(), BlockPos.ZERO.above(80), 0.0F, 0.0F));
    } else {
      ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
      ChunkPos chunkPos = new ChunkPos(serverChunkCache.randomState().sampler().findSpawnPosition());
      LOGGER.info("Preparing spawn: {}", serverLevel.dimension().location());
      int i = serverChunkCache.getGenerator().getSpawnHeight(serverLevel);
      if (i < serverLevel.getMinY()) {
        BlockPos blockPos = chunkPos.getWorldPosition();
        i = serverLevel.getHeight(Types.WORLD_SURFACE, blockPos.getX() + 8, blockPos.getZ() + 8);
      }

      serverLevelData
          .setSpawn(RespawnData.of(serverLevel.dimension(), chunkPos.getWorldPosition().offset(8, i, 8), 0.0F, 0.0F));
      int j = 0;
      int k = 0;
      int l = 0;
      int m = -1;

      for (int n = 0; n < Mth.square(11); n++) {
        if (j >= -5 && j <= 5 && k >= -5 && k <= 5) {
          spawnChunksToCheck.add(new ChunkPos(chunkPos.x + j, chunkPos.z + k));
        }

        if (j == k || j < 0 && j == -k || j > 0 && j == 1 - k) {
          int o = l;
          l = -m;
          m = o;
        }

        j += l;
        k += m;
      }
    }
  }

  private void processSpawnChunk() {
    if (processingChunk != null) {
      return;
    }
    var serverLevelData = (ServerLevelData) serverLevel.getLevelData();
    var chunkPos = spawnChunksToCheck.remove(0);
    var chunkSource = serverLevel.getChunkSource();

    if (!serverLevel.getServer().isReady()) {
      BlockPos blockPos2 = PlayerSpawnFinder.getSpawnPosInChunk(serverLevel, chunkPos);
      if (blockPos2 != null) {
        serverLevelData.setSpawn(RespawnData.of(serverLevel.dimension(), blockPos2, 0.0F, 0.0F));
        spawnChunksToCheck.clear();
      }
      return;
    }

    processingChunk = chunkSource.addTicketAndLoadWithRadius(
        TicketType.PLAYER_SPAWN, chunkPos, 0)
        .whenCompleteAsync((result, throwable) -> {
          if (throwable == null) {
            BlockPos blockPos2 = PlayerSpawnFinder.getSpawnPosInChunk(serverLevel, chunkPos);
            if (blockPos2 != null) {
              serverLevelData.setSpawn(RespawnData.of(serverLevel.dimension(), blockPos2, 0.0F, 0.0F));
              spawnChunksToCheck.clear();
            }
          } else {
            LOGGER.error("Failed to load spawn chunk at {}: {}", chunkPos, throwable.getMessage());
          }
          processingChunk = null;
        }, serverLevel.getServer());
  }

  private void finishInitialization() {
    var serverLevelData = (WorldsLevelData) serverLevel.getLevelData();
    if (!DimensionDetectionUtil.isOverworldLikeDimension(serverLevel) || !serverLevelData.getPrepareSpawn()) {
      serverLevelData.setInitialized(true);
      return;
    }
    var worldData = serverLevel.getServer().getWorldData();
    var debug = worldData.isDebugWorld();
    var worldOptions = worldData.worldGenOptions();
    var generateBonusChest = worldOptions.generateBonusChest();

    var serverChunkCache = serverLevel.getChunkSource();

    if (generateBonusChest) {
      serverLevel.registryAccess()
          .lookup(Registries.CONFIGURED_FEATURE)
          .flatMap(registry -> registry.get(MiscOverworldFeatures.BONUS_CHEST))
          .ifPresent(
              reference -> ((ConfiguredFeature<?, ?>) reference.value())
                  .place(serverLevel, serverChunkCache.getGenerator(), serverLevel.random,
                      serverLevelData.getRespawnData().pos()));
    }

    serverLevelData.setInitialized(true);
    if (debug) {
      serverLevelData.setRaining(false);
      serverLevelData.setThundering(false);
      serverLevelData.setClearWeatherTime(1000000000);
      serverLevelData.setDayTime(6000L);
      serverLevelData.setGameType(GameType.SPECTATOR);
    }
    spawnChunksToCheck = null;
    LOGGER.info("Spawn prepared: {}", serverLevel.dimension().location());
  }
}
