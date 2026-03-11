package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.wroud.mc.worlds.server.IWeatherServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.WeatherCommand;

@Mixin(WeatherCommand.class)
public class WeatherCommandMixin {

    @Redirect(
        method = "setClear",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setWeatherParameters(IIZZ)V")
    )
    private static void redirectSetClearWeather(MinecraftServer server, int clearTime, int rainTime, boolean raining, boolean thundering, CommandSourceStack source, int duration) {
        ((IWeatherServer) server).setWeatherParameters(source.getLevel(), clearTime, rainTime, raining, thundering);
    }

    @Redirect(
        method = "setRain",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setWeatherParameters(IIZZ)V")
    )
    private static void redirectSetRainWeather(MinecraftServer server, int clearTime, int rainTime, boolean raining, boolean thundering, CommandSourceStack source, int duration) {
        ((IWeatherServer) server).setWeatherParameters(source.getLevel(), clearTime, rainTime, raining, thundering);
    }

    @Redirect(
        method = "setThunder",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setWeatherParameters(IIZZ)V")
    )
    private static void redirectSetThunderWeather(MinecraftServer server, int clearTime, int rainTime, boolean raining, boolean thundering, CommandSourceStack source, int duration) {
        ((IWeatherServer) server).setWeatherParameters(source.getLevel(), clearTime, rainTime, raining, thundering);
    }
}
