package dev.wroud.mc.worlds.manager;

import java.util.List;
import java.util.concurrent.Executor;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;

@FunctionalInterface
public interface ServerLevelProvider<T extends CustomServerLevel> {

  T create(
      MinecraftServer minecraftServer,
      Executor executor,
      LevelStorageSource.LevelStorageAccess levelStorageAccess,
      WorldsLevelData serverLevelData,
      ResourceKey<Level> resourceKey,
      LevelStem levelStem,
      List<CustomSpawner> customSpawners,
      @Nullable RandomSequences randomSequences);
}