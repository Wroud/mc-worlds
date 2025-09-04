package dev.wroud.mc.worlds.manadger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.border.WorldBorder.Settings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;

public class LevelData implements ServerLevelData {
    // Core dimension and world generation data
    private static final Codec<WorldOptionsData> CORE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LevelStem.CODEC.fieldOf("level_stem").forGetter(cd -> cd.levelStem),
            Codec.LONG.optionalFieldOf("seed", 10L).forGetter(cd -> cd.seed),
            Codec.BOOL.optionalFieldOf("generate_structures", true).forGetter(cd -> cd.generateStructures))
            .apply(instance, WorldOptionsData::new));

    // World state and gameplay data
    private static final Codec<WorldStateData> WORLD_STATE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("spawn_pos", BlockPos.ZERO).forGetter(wsd -> wsd.spawnPos),
            Codec.FLOAT.optionalFieldOf("spawn_angle", 0.0f).forGetter(wsd -> wsd.spawnAngle))
            .apply(instance, WorldStateData::new));

    // Weather and special entity data
    private static final Codec<WanderingTraderData> WANDERING_TRADER_CODEC = RecordCodecBuilder
            .create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("wandering_trader_spawn_delay", 0)
                            .forGetter(wed -> wed.wanderingTraderSpawnDelay),
                    Codec.INT.optionalFieldOf("wandering_trader_spawn_chance", 0)
                            .forGetter(wed -> wed.wanderingTraderSpawnChance),
                    UUIDUtil.CODEC.optionalFieldOf("wandering_trader_id").forGetter(wed -> wed.wanderingTraderId))
                    .apply(instance, WanderingTraderData::new));

    // Weather and special entity data
    private static final Codec<WeatherData> WEATHER_ENTITY_CODEC = RecordCodecBuilder
            .create(instance -> instance.group(
                    Codec.LONG.optionalFieldOf("game_time", 0L).forGetter(wed -> wed.gameTime),
                    Codec.LONG.optionalFieldOf("day_time", 0L).forGetter(wed -> wed.dayTime),
                    Codec.BOOL.optionalFieldOf("is_thundering", false).forGetter(wed -> wed.isThundering),
                    Codec.BOOL.optionalFieldOf("is_raining", false).forGetter(wed -> wed.isRaining),
                    Codec.INT.optionalFieldOf("rain_time", 0).forGetter(wed -> wed.rainTime),
                    Codec.INT.optionalFieldOf("thunder_time", 0).forGetter(wed -> wed.thunderTime),
                    Codec.INT.optionalFieldOf("clear_weather_time", 0).forGetter(wed -> wed.clearWeatherTime))
                    .apply(instance, WeatherData::new));

    // Game settings and world border data
    private static final Codec<GameSettingsData> GAME_SETTINGS_CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    CompoundTag.CODEC.optionalFieldOf("world_border", null).forGetter(gsd -> gsd.getTag()),
                    Codec.BOOL.optionalFieldOf("initialized", false).forGetter(gsd -> gsd.initialized))
            .apply(instance, GameSettingsData::new));

    public static final Codec<LevelData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CORE_CODEC.fieldOf("core").forGetter(ld -> ld.worldOptionsData),
            WORLD_STATE_CODEC.fieldOf("world_state").forGetter(ld -> ld.worldStateData),
            WANDERING_TRADER_CODEC.fieldOf("wandering_trader").forGetter(ld -> ld.wanderingTraderData),
            WEATHER_ENTITY_CODEC.fieldOf("weather").forGetter(ld -> ld.weatherData),
            GAME_SETTINGS_CODEC.fieldOf("game_settings").forGetter(ld -> ld.gameSettingsData),
            CompoundTag.CODEC.listOf().optionalFieldOf("scheduled_events", new ArrayList<>())
                    .forGetter(ld -> ld.scheduledEvents.store().compoundStream().toList()))
            .apply(instance, LevelData::new));

    private static class WorldOptionsData {
        private LevelStem levelStem;
        private long seed;
        private boolean generateStructures;

        public WorldOptionsData(LevelStem levelStem, long seed, boolean generateStructures) {
            this.levelStem = levelStem;
            this.seed = seed;
            this.generateStructures = generateStructures;
        }
    }

    private static class WorldStateData {
        private BlockPos spawnPos;
        private float spawnAngle;

        public WorldStateData(BlockPos spawnPos, float spawnAngle) {
            this.spawnPos = spawnPos;
            this.spawnAngle = spawnAngle;
        }
    }

    private static class WeatherData {
        private long gameTime;
        private long dayTime;
        private boolean isThundering;
        private boolean isRaining;
        private int rainTime;
        private int thunderTime;
        private int clearWeatherTime;

        public WeatherData(long gameTime, long dayTime, boolean isThundering, boolean isRaining,
                int rainTime, int thunderTime, int clearWeatherTime) {
            this.gameTime = gameTime;
            this.dayTime = dayTime;
            this.isThundering = isThundering;
            this.isRaining = isRaining;
            this.rainTime = rainTime;
            this.thunderTime = thunderTime;
            this.clearWeatherTime = clearWeatherTime;
        }
    }

    private static class GameSettingsData {
        private Settings worldBorderSettings;
        private boolean initialized;

        public GameSettingsData(Settings worldBorderSettings, boolean initialized) {
            this.worldBorderSettings = worldBorderSettings != null ? worldBorderSettings : WorldBorder.DEFAULT_SETTINGS;
            this.initialized = initialized;
        }

        public GameSettingsData(CompoundTag tag, boolean initialized) {
            this.worldBorderSettings = Settings.read(new Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, tag),
                    WorldBorder.DEFAULT_SETTINGS);
            this.initialized = initialized;
        }

        public CompoundTag getTag() {
            CompoundTag tag = new CompoundTag();
            if (worldBorderSettings != null) {
                worldBorderSettings.write(tag);
            }
            return tag;
        }
    }

    private static class WanderingTraderData {
        private int wanderingTraderSpawnDelay;
        private int wanderingTraderSpawnChance;
        private Optional<UUID> wanderingTraderId;

        public WanderingTraderData(int wanderingTraderSpawnDelay, int wanderingTraderSpawnChance,
                Optional<UUID> wanderingTraderId) {
            this.wanderingTraderSpawnDelay = wanderingTraderSpawnDelay;
            this.wanderingTraderSpawnChance = wanderingTraderSpawnChance;
            this.wanderingTraderId = wanderingTraderId;
        }
    }

    private WorldOptionsData worldOptionsData;
    private WorldStateData worldStateData;
    private WanderingTraderData wanderingTraderData;
    private WeatherData weatherData;
    private GameSettingsData gameSettingsData;
    private TimerQueue<MinecraftServer> scheduledEvents;

    private WorldData worldData;

    public LevelData(WorldOptionsData worldOptionsData, WorldStateData worldStateData,
            WanderingTraderData wanderingTraderData,
            WeatherData weatherData) {
        this(worldOptionsData, worldStateData, wanderingTraderData, weatherData, null, new ArrayList<>());
    }

    public LevelData(WorldOptionsData worldOptionsData, WorldStateData worldStateData,
            WanderingTraderData wanderingTraderData, WeatherData weatherData,
            GameSettingsData gameSettingsData, List<CompoundTag> scheduledEvents) {
        this(worldOptionsData, worldStateData, wanderingTraderData, weatherData, gameSettingsData,
                new TimerQueue<MinecraftServer>(TimerCallbacks.SERVER_CALLBACKS,
                        scheduledEvents.stream()
                                .map(tag -> new Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, tag))));
    }

    public LevelData(WorldOptionsData worldOptionsData, WorldStateData worldStateData,
            WanderingTraderData wanderingTraderData, WeatherData weatherData,
            GameSettingsData gameSettingsData, TimerQueue<MinecraftServer> scheduledEvents) {
        this.worldOptionsData = worldOptionsData;
        this.worldStateData = worldStateData;
        this.wanderingTraderData = wanderingTraderData;
        this.weatherData = weatherData;
        this.gameSettingsData = gameSettingsData;
        this.scheduledEvents = scheduledEvents;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public LevelStem getLevelStem() {
        return worldOptionsData.levelStem;
    }

    public long getSeed() {
        return worldOptionsData.seed;
    }

    public boolean getGenerateStructures() {
        return worldOptionsData.generateStructures;
    }

    public boolean isDebugWorld() {
        return false; // Default to false for normal worlds
    }

    @Override
    public void setSpawn(BlockPos blockPos, float f) {
        this.worldStateData.spawnPos = blockPos;
        this.worldStateData.spawnAngle = f;
    }

    @Override
    public BlockPos getSpawnPos() {
        return this.worldStateData.spawnPos;
    }

    @Override
    public float getSpawnAngle() {
        return this.worldStateData.spawnAngle;
    }

    @Override
    public long getGameTime() {
        return this.weatherData.gameTime;
    }

    @Override
    public long getDayTime() {
        return this.weatherData.dayTime;
    }

    @Override
    public boolean isThundering() {
        return this.weatherData.isThundering;
    }

    @Override
    public boolean isRaining() {
        return this.weatherData.isRaining;
    }

    @Override
    public void setRaining(boolean bl) {
        this.weatherData.isRaining = bl;
    }

    @Override
    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    @Override
    public Difficulty getDifficulty() {
        return this.worldData.overworldData().getDifficulty();
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.worldData.overworldData().isDifficultyLocked();
    }

    @Override
    public String getLevelName() {
        return this.worldData.getLevelName();
    }

    @Override
    public void setThundering(boolean bl) {
        this.weatherData.isThundering = bl;
    }

    @Override
    public int getRainTime() {
        return this.weatherData.rainTime;
    }

    @Override
    public void setRainTime(int i) {
        this.weatherData.rainTime = i;
    }

    @Override
    public void setThunderTime(int i) {
        this.weatherData.thunderTime = i;
    }

    @Override
    public int getThunderTime() {
        return this.weatherData.thunderTime;
    }

    @Override
    public int getClearWeatherTime() {
        return this.weatherData.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int i) {
        this.weatherData.clearWeatherTime = i;
    }

    @Override
    public int getWanderingTraderSpawnDelay() {
        return this.wanderingTraderData.wanderingTraderSpawnDelay;
    }

    @Override
    public void setWanderingTraderSpawnDelay(int i) {
        this.wanderingTraderData.wanderingTraderSpawnDelay = i;
    }

    @Override
    public int getWanderingTraderSpawnChance() {
        return this.wanderingTraderData.wanderingTraderSpawnChance;
    }

    @Override
    public void setWanderingTraderSpawnChance(int i) {
        this.wanderingTraderData.wanderingTraderSpawnChance = i;
    }

    @Override
    public UUID getWanderingTraderId() {
        return this.wanderingTraderData.wanderingTraderId.orElse(null);
    }

    @Override
    public void setWanderingTraderId(UUID uUID) {
        this.wanderingTraderData.wanderingTraderId = Optional.ofNullable(uUID);
    }

    @Override
    public GameType getGameType() {
        return this.worldData.getGameType();
    }

    @Override
    public void setWorldBorder(Settings settings) {
        this.gameSettingsData.worldBorderSettings = settings;
    }

    @Override
    public Settings getWorldBorder() {
        return this.gameSettingsData.worldBorderSettings;
    }

    @Override
    public boolean isInitialized() {
        return this.gameSettingsData.initialized;
    }

    @Override
    public void setInitialized(boolean bl) {
        this.gameSettingsData.initialized = bl;
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
        this.weatherData.gameTime = l;
    }

    @Override
    public void setDayTime(long l) {
        this.weatherData.dayTime = l;
    }

    @Override
    public GameRules getGameRules() {
        return this.worldData.getGameRules();
    }

    public void setWorldData(WorldData worldData) {
        this.worldData = worldData;
    }

    public static LevelData getDefault(ResourceLocation id, LevelStem levelStem, long seed,
            boolean generateStructures) {
        return new LevelData(
                new WorldOptionsData(levelStem, seed, generateStructures),
                new WorldStateData(BlockPos.ZERO,
                        0.0F),
                new WanderingTraderData(0, 0, Optional.ofNullable(null)),
                new WeatherData(0L, 0L, false, false, 0, 0, 0),
                new GameSettingsData(WorldBorder.DEFAULT_SETTINGS, false),
                new TimerQueue<MinecraftServer>(TimerCallbacks.SERVER_CALLBACKS));
    }
}
