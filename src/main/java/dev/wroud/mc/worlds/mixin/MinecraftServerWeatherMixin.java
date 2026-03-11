package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;

import dev.wroud.mc.worlds.server.IWeatherServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;

@Mixin(MinecraftServer.class)
public class MinecraftServerWeatherMixin implements IWeatherServer {

    @Override
    public void setWeatherParameters(ServerLevel level, int clearTime, int rainTime, boolean raining, boolean thundering) {
        WeatherData weatherData = level.getWeatherData();
        weatherData.setClearWeatherTime(clearTime);
        weatherData.setRainTime(rainTime);
        weatherData.setThunderTime(rainTime);
        weatherData.setRaining(raining);
        weatherData.setThundering(thundering);
    }
}
