package dev.wroud.mc.worlds.manager.level.data;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.wroud.mc.worlds.core.registries.WorldsRegistries;
import dev.wroud.mc.worlds.manager.DefaultServerLevelProvider;
import dev.wroud.mc.worlds.manager.ServerLevelProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

public class WorldGeneratorData {
  private static final Codec<Either<LevelStem, Dynamic<?>>> LEVEL_STEM_CODEC = Codec.either(LevelStem.CODEC,
      Codec.PASSTHROUGH);

  public static final Codec<WorldGeneratorData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      LEVEL_STEM_CODEC.optionalFieldOf("level_stem").forGetter(cd -> cd.levelStem),
      Codec.LONG.optionalFieldOf("seed", 10L).forGetter(cd -> cd.seed),
      Codec.BOOL.optionalFieldOf("generate_structures", true).forGetter(cd -> cd.generateStructures),
      Codec.BOOL.optionalFieldOf("prepare_spawn", true).forGetter(cd -> cd.prepareSpawn),
      Codec.BOOL.optionalFieldOf("lazy", true).forGetter(cd -> cd.lazy),
      ResourceKey.codec(WorldsRegistries.LEVEL_PROVIDER)
          .optionalFieldOf("provider", DefaultServerLevelProvider.DEFAULT).forGetter(cd -> cd.provider))
      .apply(instance, WorldGeneratorData::new));

  private final Optional<Either<LevelStem, Dynamic<?>>> levelStem;
  public final long seed;
  public final boolean generateStructures;
  public final boolean prepareSpawn;
  public boolean lazy;
  public ResourceKey<ServerLevelProvider<?>> provider;

  public WorldGeneratorData(@Nullable LevelStem levelStem, long seed, boolean generateStructures, boolean prepareSpawn,
      boolean lazy, ResourceKey<ServerLevelProvider<?>> provider) {
    this(Optional.ofNullable(levelStem).map(Either::<LevelStem, Dynamic<?>>left), seed, generateStructures,
        prepareSpawn, lazy, provider);
  }

  private WorldGeneratorData(Optional<Either<LevelStem, Dynamic<?>>> levelStem, long seed, boolean generateStructures,
      boolean prepareSpawn, boolean lazy, ResourceKey<ServerLevelProvider<?>> provider) {
    this.levelStem = levelStem;
    this.seed = seed;
    this.generateStructures = generateStructures;
    this.prepareSpawn = prepareSpawn;
    this.lazy = lazy;
    this.provider = provider;
  }

  public @Nullable LevelStem getLevelStem() {
    return levelStem.flatMap(either -> either.left()).orElse(null);
  }
}
