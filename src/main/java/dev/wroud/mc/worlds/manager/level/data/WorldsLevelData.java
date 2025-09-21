package dev.wroud.mc.worlds.manager.level.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.wroud.mc.worlds.manager.DefaultServerLevelProvider;
import dev.wroud.mc.worlds.manager.ServerLevelProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder.Settings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;

public class WorldsLevelData implements ServerLevelData {

  public static final Codec<WorldsLevelData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      WorldGeneratorData.CODEC.fieldOf("core").forGetter(ld -> ld.generator),
      WorldSettingsData.CODEC.optionalFieldOf("settings", new WorldSettingsData()).forGetter(ld -> ld.settings),
      WorldWanderingTraderData.CODEC.fieldOf("wandering_trader").forGetter(ld -> ld.wanderingTrader),
      WorldWeatherData.CODEC.fieldOf("weather").forGetter(ld -> ld.weather),
      CompoundTag.CODEC.listOf().optionalFieldOf("scheduled_events", new ArrayList<>())
          .forGetter(ld -> ld.scheduledEvents.store().compoundStream().toList()))
      .apply(instance, WorldsLevelData::new));

  private WorldGeneratorData generator;
  private WorldSettingsData settings;
  private WorldWanderingTraderData wanderingTrader;
  private WorldWeatherData weather;
  private TimerQueue<MinecraftServer> scheduledEvents;

  private WorldData worldData;

  public WorldsLevelData(WorldGeneratorData generator, WorldSettingsData settings,
      WorldWanderingTraderData wanderingTrader,
      WorldWeatherData weather) {
    this(generator, settings, wanderingTrader, weather,
        new TimerQueue<MinecraftServer>(TimerCallbacks.SERVER_CALLBACKS));
  }

  public WorldsLevelData(WorldGeneratorData generator, WorldSettingsData settings,
      WorldWanderingTraderData wanderingTrader, WorldWeatherData weather,
      List<CompoundTag> scheduledEvents) {
    this(generator, settings, wanderingTrader, weather,
        new TimerQueue<MinecraftServer>(TimerCallbacks.SERVER_CALLBACKS,
            scheduledEvents.stream()
                .map(tag -> new Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, tag))));
  }

  public WorldsLevelData(WorldGeneratorData generator, WorldSettingsData state,
      WorldWanderingTraderData wanderingTrader, WorldWeatherData weather,
      TimerQueue<MinecraftServer> scheduledEvents) {
    this.generator = generator;
    this.settings = state;
    this.wanderingTrader = wanderingTrader;
    this.weather = weather;
    this.scheduledEvents = scheduledEvents;
  }

  public WorldData getWorldData() {
    return this.worldData;
  }

  public ResourceKey<ServerLevelProvider<?>> getProvider() {
    return generator.provider;
  }

  public LevelStem getLevelStem() {
    return generator.levelStem;
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
    return this.weather.gameTime;
  }

  @Override
  public long getDayTime() {
    return this.weather.dayTime;
  }

  @Override
  public boolean isThundering() {
    return this.weather.isThundering;
  }

  @Override
  public boolean isRaining() {
    return this.weather.isRaining;
  }

  @Override
  public void setRaining(boolean bl) {
    this.weather.isRaining = bl;
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
  public void setThundering(boolean bl) {
    this.weather.isThundering = bl;
  }

  @Override
  public int getRainTime() {
    return this.weather.rainTime;
  }

  @Override
  public void setRainTime(int i) {
    this.weather.rainTime = i;
  }

  @Override
  public void setThunderTime(int i) {
    this.weather.thunderTime = i;
  }

  @Override
  public int getThunderTime() {
    return this.weather.thunderTime;
  }

  @Override
  public int getClearWeatherTime() {
    return this.weather.clearWeatherTime;
  }

  @Override
  public void setClearWeatherTime(int i) {
    this.weather.clearWeatherTime = i;
  }

  @Override
  public int getWanderingTraderSpawnDelay() {
    return this.wanderingTrader.wanderingTraderSpawnDelay;
  }

  @Override
  public void setWanderingTraderSpawnDelay(int i) {
    this.wanderingTrader.wanderingTraderSpawnDelay = i;
  }

  @Override
  public int getWanderingTraderSpawnChance() {
    return this.wanderingTrader.wanderingTraderSpawnChance;
  }

  @Override
  public void setWanderingTraderSpawnChance(int i) {
    this.wanderingTrader.wanderingTraderSpawnChance = i;
  }

  @Override
  public UUID getWanderingTraderId() {
    return this.wanderingTrader.wanderingTraderId.orElse(null);
  }

  @Override
  public void setWanderingTraderId(UUID uUID) {
    this.wanderingTrader.wanderingTraderId = Optional.ofNullable(uUID);
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
  public void setGameType(GameType gameType) {
  }

  @Override
  public TimerQueue<MinecraftServer> getScheduledEvents() {
    return scheduledEvents;
  }

  @Override
  public void setGameTime(long l) {
    this.weather.gameTime = l;
  }

  @Override
  public void setDayTime(long l) {
    this.weather.dayTime = l;
  }

  @Override
  public GameRules getGameRules() {
    return this.worldData.getGameRules();
  }

  public void setWorldData(WorldData worldData) {
    this.worldData = worldData;
  }

  @Override
  public void setLegacyWorldBorderSettings(Optional<Settings> arg0) {
  }

  @Override
  public Optional<Settings> getLegacyWorldBorderSettings() {
    return Optional.empty();
  }

  public static WorldsLevelData getDefault(ResourceKey<ServerLevelProvider<?>> provider, ResourceLocation id,
      LevelStem levelStem, long seed,
      boolean generateStructures) {
    return new WorldsLevelData(
        new WorldGeneratorData(levelStem, seed, generateStructures, true, provider),
        new WorldSettingsData(),
        new WorldWanderingTraderData(),
        new WorldWeatherData());
  }

  public static WorldsLevelData getDefault(ResourceLocation id, LevelStem levelStem, long seed,
      boolean generateStructures) {
    return getDefault(DefaultServerLevelProvider.DEFAULT, id, levelStem, seed, generateStructures);
  }
}
