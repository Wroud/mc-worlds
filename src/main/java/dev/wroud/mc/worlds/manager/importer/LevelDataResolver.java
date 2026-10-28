package dev.wroud.mc.worlds.manager.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;

import dev.wroud.mc.worlds.manager.DefaultServerLevelProvider;
import dev.wroud.mc.worlds.manager.WorldsData;
import dev.wroud.mc.worlds.manager.level.data.WorldGeneratorData;
import dev.wroud.mc.worlds.manager.level.data.WorldSettingsData;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.server.level.PerWorldClocks;
import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

public final class LevelDataResolver {
  private static final List<ResourceKey<LevelStem>> VANILLA_STEMS = List.of(LevelStem.OVERWORLD, LevelStem.NETHER,
      LevelStem.END);
  private static final List<StateFile> ROOT_STATE = List.of(
      new StateFile(GameRuleMap.TYPE, PerWorldGameRules.TYPE),
      new StateFile(ServerClockManager.TYPE, PerWorldClocks.TYPE),
      new StateFile(WeatherData.TYPE, WeatherData.TYPE));

  private LevelDataResolver() {
  }

  public static boolean generatorAvailable(SourceDimension dimension, RegistryAccess reg) {
    ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, dimension.id());
    return VANILLA_STEMS.contains(stemKey) || dimension.storedGenerator()
        || reg.lookupOrThrow(Registries.LEVEL_STEM).get(stemKey).isPresent();
  }

  public static ResolvedDimension resolve(StagedImport s, SourceSave save, SourceDimension source,
      ResourceKey<Level> newKey, RegistryAccess reg) throws ImportException {
    String folder = save.folder();
    Identifier sourceDim = source.id();
    ResourceKey<Level> sourceKey = ResourceKey.create(Registries.DIMENSION, sourceDim);
    ResourceKey<LevelStem> stemKey = Registries.levelToLevelStem(sourceKey);
    boolean vanilla = VANILLA_STEMS.contains(stemKey);
    Path dimData = dimensionData(s, sourceKey);

    WorldsData worlds = s.rootData().get(WorldsData.TYPE);
    WorldsLevelData entry = worlds != null ? worlds.getLevelData(sourceDim) : null;
    if (entry != null) {
      LevelStem stem = entry.getLevelStem();
      if (stem == null) {
        throw new ImportException("exception.generator", sourceDim, folder, "level_stem");
      }
      RespawnData spawn = entry.getRespawnData();
      entry.setSpawn(spawn.dimension().identifier().equals(sourceDim) ? rekey(spawn, newKey) : RespawnData.DEFAULT);
      entry.setInitialized(true);
      return new ResolvedDimension(sourceDim, newKey, stem, entry, List.of());
    }

    Generator generator = Generator.NONE;
    if (Files.exists(file(dimData, WorldGenSettings.TYPE))) {
      ResourceKey<LevelStem> key = vanilla ? stemKey : environment(s, sourceKey, folder, reg);
      generator = readGenerator(storage(dimData, reg), dimData, key, reg);
    }
    Path rootData = s.access().getLevelPath(LevelResource.DATA);
    if (generator.stem() == null && Files.exists(file(rootData, WorldGenSettings.TYPE))) {
      Generator root = readGenerator(s.rootData(), rootData, stemKey, reg);
      if (root.stem() != null || generator.options() == null) {
        generator = root;
      }
    }

    List<Component> warnings = new ArrayList<>();
    LevelStem stem = generator.stem();
    if (stem == null) {
      String reason = generator.error() != null ? generator.error() : "no world_gen_settings";
      var serverStem = reg.lookupOrThrow(Registries.LEVEL_STEM).get(stemKey).map(Holder::value);
      if (serverStem.isEmpty()) {
        throw generatorAvailable(source, reg)
            ? new ImportException("exception.generator", sourceDim, folder, reason)
            : new ImportException("exception.datapack_dimension", sourceDim, folder);
      }
      stem = serverStem.get();
      if (vanilla) {
        warnings.add(Component.translatable(ImportException.KEY_PREFIX + "warning.generator",
            newKey.identifier().toString(), sourceDim.toString()));
      }
    }
    WorldOptions options = generator.options() != null ? generator.options()
        : new WorldOptions(WorldOptions.randomSeed(), true, false);

    RespawnData spawn;
    long gameTime;
    Path overrides = dimData.resolve("paper").resolve("level_overrides.dat");
    if (Files.exists(overrides)) {
      Dynamic<Tag> paper = new Dynamic<>(NbtOps.INSTANCE, readPaperOverrides(overrides, folder));
      OptionalDynamic<Tag> paperSpawn = paper.get("spawn");
      spawn = paperSpawn.get("pos").read(BlockPos.CODEC).result()
          .map(pos -> RespawnData.of(newKey, pos, paperSpawn.get("yaw").asFloat(0.0F),
              paperSpawn.get("pitch").asFloat(0.0F)))
          .orElse(RespawnData.DEFAULT);
      gameTime = paper.get("game_time").asLong(0L);
    } else {
      Dynamic<?> level = s.levelTag();
      spawn = level.get("spawn").read(RespawnData.CODEC).result()
          .filter(levelSpawn -> keepsSpawn(levelSpawn, sourceDim, save))
          .map(levelSpawn -> rekey(levelSpawn, newKey))
          .orElse(RespawnData.DEFAULT);
      gameTime = level.get("Time").asLong(0L);
    }

    WorldsLevelData data = new WorldsLevelData(
        new WorldGeneratorData(stem, options.seed(), options.generateStructures(), true, true,
            DefaultServerLevelProvider.DEFAULT),
        new WorldSettingsData(spawn, true), gameTime);
    return new ResolvedDimension(sourceDim, newKey, stem, data, List.copyOf(warnings));
  }

  public static void copyRootState(StagedImport s, Identifier sourceDim) throws ImportException {
    Path dimData = dimensionData(s, ResourceKey.create(Registries.DIMENSION, sourceDim));
    Path rootData = s.access().getLevelPath(LevelResource.DATA);
    try {
      for (StateFile state : ROOT_STATE) {
        Path target = file(dimData, state.vanilla());
        Path root = file(rootData, state.vanilla());
        if (!Files.exists(target) && !Files.exists(file(dimData, state.own())) && Files.exists(root)) {
          Files.createDirectories(target.getParent());
          Files.copy(root, target);
        }
      }
    } catch (IOException e) {
      throw ImportException.failed(sourceDim, e);
    }
  }

  private static boolean keepsSpawn(RespawnData spawn, Identifier sourceDim, SourceSave save) {
    Identifier spawnDim = spawn.dimension().identifier();
    if (spawnDim.equals(sourceDim)) {
      return true;
    }
    if (save.dimensionIds().contains(spawnDim)) {
      return false;
    }
    return save.dimensions().stream().filter(dimension -> !dimension.empty()).map(SourceDimension::id).toList()
        .equals(List.of(sourceDim));
  }

  private static RespawnData rekey(RespawnData spawn, ResourceKey<Level> newKey) {
    return RespawnData.of(newKey, spawn.pos(), spawn.yaw(), spawn.pitch());
  }

  private static Generator readGenerator(SavedDataStorage storage, Path dataDir, ResourceKey<LevelStem> key,
      RegistryAccess reg) {
    WorldGenSettings settings = storage.get(WorldGenSettings.TYPE);
    if (settings != null && settings.dimensions().dimensions().get(key) instanceof LevelStem stem) {
      return new Generator(settings.options(), stem, null);
    }

    try {
      CompoundTag data = storage.readTagFromDisk(file(dataDir, WorldGenSettings.TYPE),
          DataFixTypes.SAVED_DATA_WORLD_GEN_SETTINGS, currentVersion()).getCompoundOrEmpty("data");
      RegistryOps<Tag> ops = reg.createSerializationContext(NbtOps.INSTANCE);
      WorldOptions options = WorldOptions.CODEC.codec().parse(ops, data).result().orElse(null);
      Tag entry = data.getCompoundOrEmpty("dimensions").get(key.identifier().toString());
      if (entry == null) {
        return new Generator(options, null, "no " + key.identifier() + " in " + WorldGenSettings.TYPE.id());
      }
      DataResult<LevelStem> stem = LevelStem.CODEC.parse(ops, entry);
      return new Generator(options, stem.result().orElse(null),
          stem.error().map(DataResult.Error::message).orElse(null));
    } catch (IOException | RuntimeException e) {
      return new Generator(null, null, String.valueOf(e.getMessage()));
    }
  }

  private static ResourceKey<LevelStem> environment(StagedImport s, ResourceKey<Level> sourceKey, String folder,
      RegistryAccess reg) throws ImportException {
    Path dimDir = s.access().getDimensionPath(sourceKey);
    if (Files.exists(file(dimDir.resolve(LevelResource.DATA.id()), EnderDragonFight.TYPE))) {
      return LevelStem.END;
    }

    CompoundTag context = ChunkMap.getChunkDataFixContextTag(Level.OVERWORLD, Optional.empty());
    Registry<Biome> biomes = reg.lookupOrThrow(Registries.BIOME);
    Path regionDir = dimDir.resolve("region");

    try (SimpleRegionStorage storage = new SimpleRegionStorage(
        new RegionStorageInfo(s.access().getLevelId(), sourceKey, "chunk"), regionDir, DataFixers.getDataFixer(),
        false, DataFixTypes.CHUNK);
        Stream<Path> files = Files.list(regionDir)) {
      for (Path regionFile : files.sorted().toList()) {
        for (ChunkPos pos : RegionHeaders.storedChunks(regionFile)) {
          Optional<CompoundTag> stored = storage.read(pos).join();
          if (stored.isEmpty()) {
            continue;
          }
          CompoundTag chunk = storage.upgradeChunkTag(stored.get(), -1, context, currentVersion());
          if (!SerializableChunkData.getChunkStatusFromTag(chunk).isOrAfter(ChunkStatus.BIOMES)) {
            continue;
          }
          ResourceKey<LevelStem> environment = environmentOf(chunk, biomes);
          if (environment != null) {
            return environment;
          }
        }
      }
    } catch (IOException | RuntimeException e) {
      throw new ImportException(e, "exception.generator", sourceKey.identifier(), folder, e);
    }
    throw new ImportException("exception.generator", sourceKey.identifier(), folder,
        "no chunk with a known biome in " + regionDir);
  }

  private static @Nullable ResourceKey<LevelStem> environmentOf(CompoundTag chunk, Registry<Biome> biomes) {
    ResourceKey<LevelStem> environment = null;
    for (CompoundTag section : chunk.getListOrEmpty("sections").compoundStream().toList()) {
      ListTag palette = section.getCompoundOrEmpty("biomes").getListOrEmpty("palette");
      for (int i = 0; i < palette.size(); i++) {
        Optional<Holder.Reference<Biome>> biome = palette.getString(i).map(Identifier::tryParse)
            .flatMap(biomes::get);
        if (biome.isEmpty()) {
          continue;
        }
        if (biome.get().is(BiomeTags.IS_NETHER)) {
          return LevelStem.NETHER;
        }
        if (biome.get().is(BiomeTags.IS_END)) {
          return LevelStem.END;
        }
        environment = LevelStem.OVERWORLD;
      }
    }
    return environment;
  }

  private static CompoundTag readPaperOverrides(Path file, String folder) throws ImportException {
    try {
      CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
      return tag.getCompound("data").orElse(tag);
    } catch (IOException e) {
      throw ImportException.failed(folder, e);
    }
  }

  private static SavedDataStorage storage(Path dataDir, RegistryAccess reg) {
    return new SavedDataStorage(dataDir, DataFixers.getDataFixer(), reg);
  }

  private static Path dimensionData(StagedImport s, ResourceKey<Level> dimension) {
    return s.access().getDimensionPath(dimension).resolve(LevelResource.DATA.id());
  }

  static Path file(Path dataDir, SavedDataType<?> type) {
    return type.id().withSuffix(".dat").resolveAgainst(dataDir);
  }

  static int currentVersion() {
    return SharedConstants.getCurrentVersion().dataVersion().version();
  }

  private record StateFile(SavedDataType<?> vanilla, SavedDataType<?> own) {
  }

  private record Generator(@Nullable WorldOptions options, @Nullable LevelStem stem, @Nullable String error) {
    static final Generator NONE = new Generator(null, null, null);
  }
}
