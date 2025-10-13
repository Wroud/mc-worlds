package dev.wroud.mc.worlds.manager;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.WorldPresetAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.util.LevelActivationUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public class WorldsCreator {

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      ResourceLocation id) throws InvalidLevelIdException {
    createWorld(server, callbacks, id, null, null, null);
  }

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      ResourceLocation id,
      @Nullable ResourceKey<WorldPreset> preset,
      @Nullable ResourceLocation dimension,
      @Nullable ResourceKey<LevelStem> levelStemKey,
      @Nullable Long seed) throws InvalidLevelIdException {

    if (levelStemKey != null) {
      createWorld(server, callbacks, id, levelStemKey, seed);
    } else {
      createWorld(server, callbacks, id, preset, dimension, seed);
    }
  }

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      ResourceLocation id,
      @Nullable ResourceKey<LevelStem> levelStemKey,
      @Nullable Long seed) throws InvalidLevelIdException {
    var registry = server.registryAccess();
    var levelStem = registry.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(levelStemKey).value();
    createWorld(server, callbacks, id, levelStem, seed);
  }

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      ResourceLocation id,
      @Nullable ResourceKey<WorldPreset> preset,
      @Nullable ResourceLocation dimension,
      @Nullable Long seed) throws InvalidLevelIdException {
    var registry = server.registryAccess();

    if (preset == null) {
      preset = WorldPresets.NORMAL;
    }

    var worldPreset = registry.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).value();

    if (dimension == null) {
      dimension = ((WorldPresetAccessor) worldPreset).getDimensions().keySet().stream().findFirst()
          .orElseThrow()
          .location();
    }

    final ResourceLocation finalDimension = dimension;
    var levelStem = ((WorldPresetAccessor) worldPreset).getDimensions().entrySet().stream()
        .filter(e -> e.getKey().location().equals(finalDimension))
        .map(e -> e.getValue())
        .findFirst().orElseThrow();

    createWorld(server, callbacks, id, levelStem, seed);
  }

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      ResourceLocation id,
      LevelStem levelStem,
      @Nullable Long seed) throws InvalidLevelIdException {
    validLevelId(id, server);

    if (seed == null) {
      seed = WorldOptions.randomSeed();
    }

    var dimension = levelStem.type().unwrapKey().orElseThrow().location();
    McWorldMod.LOGGER.info("Creating new world with id: {}, seed: {}, type: {}", id, seed, dimension);

    var levelData = WorldsLevelData.getDefault(id, levelStem, seed, true);

    callbacks.onCreating(id, seed, levelStem);
    var worldHandle = McWorldMod.getMcWorld(server).orElseThrow().loadOrCreate(id, levelData);

    LevelActivationUtil.executeWhenLevelReady(
        worldHandle.getServerLevel(),
        () -> callbacks.onReady(worldHandle.getServerLevel()));
  }

  public static void validLevelId(ResourceLocation id, MinecraftServer server) throws InvalidLevelIdException {
    ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    ServerLevel level = server.getLevel(resourceKey);
    if (level != null) {
      throw new InvalidLevelIdException("Level with id '" + id + "' already exists");
    }
  }

  public static class InvalidLevelIdException extends Exception {
    public InvalidLevelIdException(String message) {
      super(message);
    }
  }

  public interface CreationCallbacks {
    void onCreating(ResourceLocation id, long seed, LevelStem dimension);

    void onReady(CustomServerLevel level);
  }
}
