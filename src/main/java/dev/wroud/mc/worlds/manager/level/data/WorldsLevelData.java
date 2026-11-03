package dev.wroud.mc.worlds.manager.level.data;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.wroud.mc.worlds.manager.DefaultServerLevelProvider;
import dev.wroud.mc.worlds.manager.ServerLevelProvider;
import dev.wroud.mc.worlds.mixin.DerivedLevelDataAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;

// Cardinal Components adds a method to LevelData that only PrimaryLevelData and DerivedLevelData implement
public class WorldsLevelData extends DerivedLevelData {

  public static final Codec<WorldsLevelData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      WorldGeneratorData.CODEC.fieldOf("core").forGetter(ld -> ld.generator),
      WorldSettingsData.CODEC.optionalFieldOf("settings", new WorldSettingsData()).forGetter(ld -> ld.settings),
      Codec.LONG.optionalFieldOf("game_time", 0L).forGetter(ld -> ld.gameTime))
      .apply(instance, WorldsLevelData::new));

  private WorldGeneratorData generator;
  private WorldSettingsData settings;

  private WorldData worldData;
  private long gameTime;

  public WorldsLevelData(WorldGeneratorData generator, WorldSettingsData settings) {
    this(generator, settings, 0L);
  }

  public WorldsLevelData(WorldGeneratorData generator, WorldSettingsData state, long gameTime) {
    super(null, null);
    this.generator = generator;
    this.settings = state;
    this.gameTime = gameTime;
  }

  public WorldData getWorldData() {
    return this.worldData;
  }

  public ResourceKey<ServerLevelProvider<?>> getProvider() {
    return generator.provider;
  }

  public @Nullable LevelStem getLevelStem() {
    return generator.getLevelStem();
  }

  public long getSeed() {
    return generator.seed;
  }

  public boolean isLazy() {
    return generator.lazy;
  }

  public boolean getGenerateStructures() {
    return generator.generateStructures;
  }

  public boolean getPrepareSpawn() {
    return generator.prepareSpawn;
  }

  public void setLazy(boolean lazy) {
    this.generator.lazy = lazy;
  }

  @Override
  public RespawnData getRespawnData() {
    return this.settings.respawn;
  }

  @Override
  public void setSpawn(RespawnData respawnData) {
    this.settings.respawn = respawnData;
  }

  @Override
  public long getGameTime() {
    return this.gameTime;
  }

  @Override
  public boolean isHardcore() {
    return this.worldData.isHardcore();
  }

  @Override
  public Difficulty getDifficulty() {
    return this.worldData.getDifficulty();
  }

  @Override
  public boolean isDifficultyLocked() {
    return this.worldData.isDifficultyLocked();
  }

  @Override
  public String getLevelName() {
    return this.worldData.getLevelName();
  }

  @Override
  public GameType getGameType() {
    return this.worldData.getGameType();
  }

  @Override
  public boolean isInitialized() {
    return this.settings.initialized;
  }

  @Override
  public void setInitialized(boolean bl) {
    this.settings.initialized = bl;
  }

  @Override
  public boolean isAllowCommands() {
    return this.worldData.isAllowCommands();
  }

  @Override
  public void setAllowCommands(boolean bl) {
  }

  @Override
  public void setGameType(GameType gameType) {
  }

  @Override
  public void setGameTime(long l) {
    this.gameTime = l;
  }

  public void setWorldData(WorldData worldData) {
    this.worldData = worldData;
    ((DerivedLevelDataAccessor) this).mcworlds$setWrapped(worldData.overworldData());
  }

  public static WorldsLevelData getDefault(ResourceKey<ServerLevelProvider<?>> provider, Identifier id,
      LevelStem levelStem, long seed,
      boolean generateStructures) {
    return new WorldsLevelData(
        new WorldGeneratorData(levelStem, seed, generateStructures, true, true, provider),
        new WorldSettingsData());
  }

  public static WorldsLevelData getDefault(Identifier id, LevelStem levelStem, long seed,
      boolean generateStructures) {
    return getDefault(DefaultServerLevelProvider.DEFAULT, id, levelStem, seed, generateStructures);
  }
}
