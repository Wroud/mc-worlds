package dev.wroud.mc.worlds.server;

import java.util.List;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.manadger.LevelData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;

public class CustomServerLevel extends ServerLevel {

  public CustomServerLevel(
      MinecraftServer minecraftServer,
      Executor executor,
      LevelStorageSource.LevelStorageAccess levelStorageAccess,
      LevelData serverLevelData,
      ResourceKey<Level> resourceKey,
      LevelStem levelStem,
      ChunkProgressListener chunkProgressListener,
      boolean bl,
      List<CustomSpawner> list,
      @Nullable RandomSequences randomSequences) {
    super(minecraftServer, executor, levelStorageAccess, serverLevelData, resourceKey, levelStem, chunkProgressListener,
        bl, BiomeManager.obfuscateSeed(serverLevelData.getSeed()), list, true, randomSequences);
  }

  @Override
  public long getSeed() {
    return ((LevelData) this.levelData).getSeed();
  }
}
