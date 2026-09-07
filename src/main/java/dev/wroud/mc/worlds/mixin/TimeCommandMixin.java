package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TimeCommand.class)
public class TimeCommandMixin {

    @ModifyExpressionValue(
        method = { "suggestTimeMarkers", "queryTime", "queryTimelineTicks", "queryTimelineRepetitions", "setTotalTicks",
                   "addTime", "setTimeToTimeMarker", "setPaused", "setRate" },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager mcworlds$perWorldClock(ServerClockManager original, CommandSourceStack source) {
        return source.getLevel().clockManager();
    }
}
