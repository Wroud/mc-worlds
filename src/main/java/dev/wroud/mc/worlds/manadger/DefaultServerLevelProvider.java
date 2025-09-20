package dev.wroud.mc.worlds.manadger;

import java.util.List;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.core.registries.WorldsRegistries;
import dev.wroud.mc.worlds.manadger.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
      WorldsLevelData serverLevelData,
      ResourceKey<Level> resourceKey,
      LevelStem levelStem,
      List<CustomSpawner> customSpawners,
      @Nullable RandomSequences randomSequences) {
    return new CustomServerLevel(
        minecraftServer,
        executor,
        levelStorageAccess,
        serverLevelData,
        resourceKey,
        levelStem,
        customSpawners,
        randomSequences);
  }
}
