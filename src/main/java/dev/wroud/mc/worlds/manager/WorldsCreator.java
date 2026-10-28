package dev.wroud.mc.worlds.manager;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.mixin.WorldPresetAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.util.LevelActivationUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public class WorldsCreator {

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      Identifier id) throws InvalidLevelIdException {
    createWorld(server, callbacks, id, null, null, null);
  }

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      Identifier id,
      @Nullable ResourceKey<WorldPreset> preset,
      @Nullable Identifier dimension,
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
      Identifier id,
      @Nullable ResourceKey<LevelStem> levelStemKey,
      @Nullable Long seed) throws InvalidLevelIdException {
    var registry = server.registryAccess();
    var levelStem = registry.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(levelStemKey).value();
    createWorld(server, callbacks, id, levelStem, seed);
  }

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      Identifier id,
      @Nullable ResourceKey<WorldPreset> preset,
      @Nullable Identifier dimension,
      @Nullable Long seed) throws InvalidLevelIdException {
    var registry = server.registryAccess();

    if (preset == null) {
      preset = WorldPresets.NORMAL;
    }

    var worldPreset = registry.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).value();

    if (dimension == null) {
      dimension = ((WorldPresetAccessor) worldPreset).getDimensions().keySet().stream().findFirst()
          .orElseThrow()
          .identifier();
    }

    final Identifier finalDimension = dimension;
    var levelStem = ((WorldPresetAccessor) worldPreset).getDimensions().entrySet().stream()
        .filter(e -> e.getKey().identifier().equals(finalDimension))
        .map(e -> e.getValue())
        .findFirst().orElseThrow();

    createWorld(server, callbacks, id, levelStem, seed);
  }

  public static void createWorld(
      MinecraftServer server,
      CreationCallbacks callbacks,
      Identifier id,
      LevelStem levelStem,
      @Nullable Long seed) throws InvalidLevelIdException {
    validLevelId(id, server);

    if (seed == null) {
      seed = WorldOptions.randomSeed();
    }

    var dimension = levelStem.type().unwrapKey().orElseThrow().identifier();
    McWorldMod.LOGGER.info("Creating new world with id: {}, seed: {}, type: {}", id, seed, dimension);

    var levelData = WorldsLevelData.getDefault(id, levelStem, seed, true);

    callbacks.onCreating(id, seed, levelStem);
    server.execute(() -> {
      var worldHandle = McWorldMod.getMcWorld(server).orElseThrow().loadOrCreate(id, levelData);
      if (worldHandle != null) {
        LevelActivationUtil.executeWhenLevelReady(
            worldHandle.getServerLevel(),
            () -> callbacks.onReady(worldHandle.getServerLevel()));
      }
    });
  }

  public static @Nullable WorldHandle registerAndLoad(MinecraftServer server, Identifier id, WorldsLevelData data) {
    var mcWorld = McWorldMod.getMcWorld(server).orElseThrow();
    mcWorld.getManager().getWorldsData().addLevelData(id, data);
    return mcWorld.loadOrCreate(id, data);
  }

  public static void validLevelIdFormat(Identifier id) throws InvalidLevelIdException {
    if (!Stream.concat(Stream.of(id.getNamespace()), Arrays.stream(id.getPath().split("/", -1)))
        .allMatch(FileUtil::isValidPathSegment)) {
      throw new InvalidLevelIdException(InvalidLevelIdException.INVALID_ID,
          "Level id '" + id + "' is not a valid folder name");
    }
  }

  public static void validLevelId(Identifier id, MinecraftServer server) throws InvalidLevelIdException {
    validLevelIdFormat(id);
    ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    var registered = McWorldMod.getMcWorld(server)
        .map(mcWorld -> mcWorld.getManager().getWorldsData().getLevelData(id) != null)
        .orElse(false);
    if (registered || server.levelKeys().contains(resourceKey)) {
      throw new InvalidLevelIdException("Level with id '" + id + "' already exists");
    }
  }

  public static void validImportTarget(Identifier id, MinecraftServer server) throws InvalidLevelIdException {
    validLevelId(id, server);
    ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    var session = ((MinecraftServerAccessor) server).getStorageSource();
    if (Files.exists(session.getDimensionPath(resourceKey))) {
      throw new InvalidLevelIdException(InvalidLevelIdException.FOLDER_EXISTS,
          "Folder for level '" + id + "' already exists");
    }
  }

  public static class InvalidLevelIdException extends Exception {
    public static final String ALREADY_EXISTS = "dev.wroud.mc.worlds.command.create.exception.world_already_exists";
    public static final String INVALID_ID = "dev.wroud.mc.worlds.command.exception.invalid_id";
    public static final String FOLDER_EXISTS = "dev.wroud.mc.worlds.command.exception.folder_exists";

    private final String translationKey;

    public InvalidLevelIdException(String message) {
      this(ALREADY_EXISTS, message);
    }

    public InvalidLevelIdException(String translationKey, String message) {
      super(message);
      this.translationKey = translationKey;
    }

    public String getTranslationKey() {
      return translationKey;
    }
  }

  public interface CreationCallbacks {
    void onCreating(Identifier id, long seed, LevelStem dimension);

    void onReady(CustomServerLevel level);
  }
}
