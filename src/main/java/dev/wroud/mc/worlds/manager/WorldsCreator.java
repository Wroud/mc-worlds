package dev.wroud.mc.worlds.manager;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.mixin.WorldPresetAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public class WorldsCreator {
  public static final String NOT_LOADED = "dev.wroud.mc.worlds.command.create.exception.not_loaded";
  private static final String UNREGISTERED_TYPE = "dev.wroud.mc.worlds.command.create.exception.unregistered_type";
  private static final String GENERIC = "dev.wroud.mc.worlds.command.create.exception.generic";
  private static final String ALREADY_EXISTS = "dev.wroud.mc.worlds.command.create.exception.world_already_exists";
  private static final String INVALID_ID = "dev.wroud.mc.worlds.command.exception.invalid_id";
  private static final String FOLDER_EXISTS = "dev.wroud.mc.worlds.command.exception.folder_exists";

  public static LevelStem levelStem(
      MinecraftServer server,
      @Nullable ResourceKey<WorldPreset> preset,
      @Nullable Identifier dimension) {
    try {
      var worldPreset = server.registryAccess().lookupOrThrow(Registries.WORLD_PRESET)
          .getOrThrow(preset != null ? preset : WorldPresets.NORMAL).value();
      var dimensions = ((WorldPresetAccessor) worldPreset).getDimensions();
      var target = dimension != null ? ResourceKey.create(Registries.LEVEL_STEM, dimension)
          : dimensions.keySet().stream().findFirst().orElseThrow();
      return Optional.ofNullable(dimensions.get(target)).orElseThrow();
    } catch (IllegalStateException | NoSuchElementException e) {
      throw new WorldsException(GENERIC, e.getMessage());
    }
  }

  public static LevelStem levelStem(MinecraftServer server, ResourceKey<LevelStem> key) {
    try {
      return server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(key).value();
    } catch (IllegalStateException e) {
      throw new WorldsException(GENERIC, e.getMessage());
    }
  }

  public static CompletableFuture<ServerLevel> createWorld(
      MinecraftServer server,
      Identifier id,
      LevelStem levelStem,
      @Nullable Long seed) {
    if (levelStem.type().unwrapKey().isEmpty()) {
      throw new WorldsException(UNREGISTERED_TYPE, id);
    }
    return load(server, id, seed, resolved -> WorldsLevelData.getDefault(id, levelStem, resolved, true));
  }

  public static CompletableFuture<ServerLevel> createWorld(
      MinecraftServer server,
      Identifier id,
      ResourceKey<ServerLevelProvider<?>> provider,
      @Nullable Long seed) {
    return load(server, id, seed, resolved -> WorldsLevelData.getDefault(provider, id, null, resolved, true));
  }

  private static CompletableFuture<ServerLevel> load(
      MinecraftServer server,
      Identifier id,
      @Nullable Long seed,
      LongFunction<WorldsLevelData> data) {
    if (!server.isSameThread()) {
      var result = new CompletableFuture<ServerLevel>();
      server.execute(() -> {
        try {
          load(server, id, seed, data).whenComplete((level, error) -> {
            if (error != null) {
              result.completeExceptionally(error);
            } else {
              result.complete(level);
            }
          });
        } catch (WorldsException e) {
          result.completeExceptionally(e);
        }
      });
      return result;
    }

    validLevelId(id, server);
    var manager = McWorldMod.getManager(server).orElseThrow();
    var resolvedSeed = seed != null ? seed : WorldOptions.randomSeed();
    McWorldMod.LOGGER.info("Creating new world with id: {}, seed: {}", id, resolvedSeed);

    WorldHandle handle;
    try {
      handle = manager.loadOrCreateWorld(id, data.apply(resolvedSeed));
    } catch (RuntimeException e) {
      McWorldMod.LOGGER.error("Error creating world {}", id, e);
      throw new WorldsException(GENERIC, e.getMessage());
    }
    if (handle == null) {
      manager.forgetUnreadable(id);
      throw new WorldsException(NOT_LOADED, id);
    }
    manager.getTemporaryStorage().beginWorld(id);
    return handle.getServerLevel().whenReady();
  }

  public static @Nullable WorldHandle registerAndLoad(MinecraftServer server, Identifier id, WorldsLevelData data) {
    var manager = McWorldMod.getManager(server).orElseThrow();
    manager.getWorldsData().addLevelData(id, data);
    return manager.loadOrCreateWorld(id, data);
  }

  public static void validLevelIdFormat(Identifier id) {
    if (!Stream.concat(Stream.of(id.getNamespace()), Arrays.stream(id.getPath().split("/", -1)))
        .allMatch(FileUtil::isValidPathSegment)) {
      throw new WorldsException(INVALID_ID, id);
    }
  }

  private static void validLevelId(Identifier id, MinecraftServer server) {
    validLevelIdFormat(id);
    ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    var registered = McWorldMod.getManager(server).orElseThrow().getWorldsData().getLevelData(id) != null;
    if (registered || server.levelKeys().contains(resourceKey)) {
      throw new WorldsException(ALREADY_EXISTS);
    }
  }

  public static void validImportTarget(Identifier id, MinecraftServer server) {
    validLevelId(id, server);
    ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    var session = ((MinecraftServerAccessor) server).getStorageSource();
    if (Files.exists(session.getDimensionPath(resourceKey))) {
      throw new WorldsException(FOLDER_EXISTS, id);
    }
  }
}
