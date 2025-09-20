package dev.wroud.mc.worlds.manadger.level.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.wroud.mc.worlds.core.registries.WorldsRegistries;
import dev.wroud.mc.worlds.manadger.DefaultServerLevelProvider;
import dev.wroud.mc.worlds.manadger.ServerLevelProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

public class WorldGeneratorData {
  public static final Codec<WorldGeneratorData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      LevelStem.CODEC.fieldOf("level_stem").forGetter(cd -> cd.levelStem),
      Codec.LONG.optionalFieldOf("seed", 10L).forGetter(cd -> cd.seed),
      Codec.BOOL.optionalFieldOf("generate_structures", true).forGetter(cd -> cd.generateStructures),
      Codec.BOOL.optionalFieldOf("lazy", true).forGetter(cd -> cd.lazy),
      ResourceKey.codec(WorldsRegistries.LEVEL_PROVIDER)
          .optionalFieldOf("provider", DefaultServerLevelProvider.DEFAULT).forGetter(cd -> cd.provider))
      .apply(instance, WorldGeneratorData::new));

  public final LevelStem levelStem;
  public final long seed;
  public final boolean generateStructures;
  public boolean lazy;
  public ResourceKey<ServerLevelProvider<?>> provider;

  public WorldGeneratorData(LevelStem levelStem, long seed, boolean generateStructures, boolean lazy,
      ResourceKey<ServerLevelProvider<?>> provider) {
    this.levelStem = levelStem;
    this.seed = seed;
    this.generateStructures = generateStructures;
    this.lazy = lazy;
    this.provider = provider;
  }
}