package dev.wroud.mc.worlds.manadger;

import java.util.List;
import java.util.concurrent.Executor;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.core.registries.WorldsRegistries;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;

public class DefaultServerLevelProvider implements ServerLevelProvider<CustomServerLevel> {
  public static final ResourceKey<ServerLevelProvider<?>> DEFAULT = ResourceKey.create(WorldsRegistries.LEVEL_PROVIDER,
      McWorldMod.id("default"));

  @Override
  public CustomServerLevel create(
      MinecraftServer minecraftServer,
      Executor executor,
      LevelStorageSource.LevelStorageAccess levelStorageAccess,
      LevelData serverLevelData,
      ResourceKey<Level> resourceKey,
      LevelStem levelStem,
      ChunkProgressListener chunkProgressListener,
      boolean bl,
      List<CustomSpawner> customSpawners,
      RandomSequences randomSequences) {
    return new CustomServerLevel(
        minecraftServer,
        executor,
        levelStorageAccess,
        serverLevelData,
        resourceKey,
        levelStem,
        chunkProgressListener,
        bl,
        customSpawners,
        randomSequences);
  }
}
