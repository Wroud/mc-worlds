package dev.wroud.mc.worlds.manadger.level.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class WorldWeatherData {
  public static final Codec<WorldWeatherData> CODEC = RecordCodecBuilder
      .create(instance -> instance.group(
          Codec.LONG.optionalFieldOf("game_time", 0L).forGetter(wed -> wed.gameTime),
          Codec.LONG.optionalFieldOf("day_time", 0L).forGetter(wed -> wed.dayTime),
          Codec.BOOL.optionalFieldOf("is_thundering", false).forGetter(wed -> wed.isThundering),
          Codec.BOOL.optionalFieldOf("is_raining", false).forGetter(wed -> wed.isRaining),
          Codec.INT.optionalFieldOf("rain_time", 0).forGetter(wed -> wed.rainTime),
          Codec.INT.optionalFieldOf("thunder_time", 0).forGetter(wed -> wed.thunderTime),
          Codec.INT.optionalFieldOf("clear_weather_time", 0).forGetter(wed -> wed.clearWeatherTime))
          .apply(instance, WorldWeatherData::new));

  public long gameTime;
  public long dayTime;
  public boolean isThundering;
  public boolean isRaining;
  public int rainTime;
  public int thunderTime;
  public int clearWeatherTime;

  public WorldWeatherData(long gameTime, long dayTime, boolean isThundering, boolean isRaining,
      int rainTime, int thunderTime, int clearWeatherTime) {
    this.gameTime = gameTime;
    this.dayTime = dayTime;
    this.isThundering = isThundering;
    this.isRaining = isRaining;
    this.rainTime = rainTime;
    this.thunderTime = thunderTime;
    this.clearWeatherTime = clearWeatherTime;
  }

  public WorldWeatherData() {
    this.gameTime = 0L;
    this.dayTime = 0L;
    this.isThundering = false;
    this.isRaining = false;
    this.rainTime = 0;
    this.thunderTime = 0;
    this.clearWeatherTime = 0;
  }
}
