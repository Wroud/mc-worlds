package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.server.level.PerWorldClockManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;

@Mixin(TimeCommand.class)
public class TimeCommandMixin {

    @Redirect(method = "setTotalTicks",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;setTotalTicks(Lnet/minecraft/core/Holder;J)V"))
    private static void redirectSetTotalTicks(ServerClockManager mgr, Holder<WorldClock> clock, long totalTicks,
            CommandSourceStack source) {
        PerWorldClockManager pwcm = getPerWorld(source);
        if (pwcm != null) pwcm.setTotalTicks(clock, totalTicks);
        else mgr.setTotalTicks(clock, totalTicks);
    }

    @Redirect(method = "addTime",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;addTicks(Lnet/minecraft/core/Holder;I)V"))
    private static void redirectAddTicks(ServerClockManager mgr, Holder<WorldClock> clock, int ticks,
            CommandSourceStack source) {
        PerWorldClockManager pwcm = getPerWorld(source);
        if (pwcm != null) pwcm.addTicks(clock, ticks);
        else mgr.addTicks(clock, ticks);
    }

    @Redirect(method = "addTime",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;getTotalTicks(Lnet/minecraft/core/Holder;)J"))
    private static long redirectGetTotalTicksInAdd(ServerClockManager mgr, Holder<WorldClock> clock,
            CommandSourceStack source) {
        PerWorldClockManager pwcm = getPerWorld(source);
        return pwcm != null ? pwcm.getTotalTicks(clock) : mgr.getTotalTicks(clock);
    }

    @Redirect(method = "setTimeToTimeMarker",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z"))
    private static boolean redirectMoveToTimeMarker(ServerClockManager mgr, Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> timeMarkerId,
            CommandSourceStack source) {
        PerWorldClockManager pwcm = getPerWorld(source);
        if (pwcm != null) return pwcm.moveToTimeMarker(clock, timeMarkerId);
        return mgr.moveToTimeMarker(clock, timeMarkerId);
    }

    @Redirect(method = "setTimeToTimeMarker",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;getTotalTicks(Lnet/minecraft/core/Holder;)J"))
    private static long redirectGetTotalTicksInTimeMarker(ServerClockManager mgr, Holder<WorldClock> clock,
            CommandSourceStack source) {
        PerWorldClockManager pwcm = getPerWorld(source);
        return pwcm != null ? pwcm.getTotalTicks(clock) : mgr.getTotalTicks(clock);
    }

    @Redirect(method = "setPaused",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;setPaused(Lnet/minecraft/core/Holder;Z)V"))
    private static void redirectSetPaused(ServerClockManager mgr, Holder<WorldClock> clock, boolean paused,
            CommandSourceStack source) {
        PerWorldClockManager pwcm = getPerWorld(source);
        if (pwcm != null) pwcm.setPaused(clock, paused);
        else mgr.setPaused(clock, paused);
    }

    @Redirect(method = "setRate",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;setRate(Lnet/minecraft/core/Holder;F)V"))
    private static void redirectSetRate(ServerClockManager mgr, Holder<WorldClock> clock, float rate,
            CommandSourceStack source) {
        PerWorldClockManager pwcm = getPerWorld(source);
        if (pwcm != null) pwcm.setRate(clock, rate);
        else mgr.setRate(clock, rate);
    }

    @Redirect(method = "queryTime",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ServerClockManager;getTotalTicks(Lnet/minecraft/core/Holder;)J"))
    private static long redirectGetTotalTicksInQuery(ServerClockManager mgr, Holder<WorldClock> clock,
            CommandSourceStack source) {
        PerWorldClockManager pwcm = getPerWorld(source);
        return pwcm != null ? pwcm.getTotalTicks(clock) : mgr.getTotalTicks(clock);
    }

    private static PerWorldClockManager getPerWorld(CommandSourceStack source) {
        if (source.getLevel() instanceof CustomServerLevel csl) {
            return csl.getPerWorldClockManager();
        }
        return null;
    }
}
