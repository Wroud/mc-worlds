package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;

@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {

  @Accessor("emptyTime")
  int getEmptyTime();

  @Invoker("prepareWeather")
  void invokePrepareWeather(WeatherData weatherData);
}
