package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.server.level.PerWorldClockManager;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;

/**
 * Redirects the sleep-wake time skip in ServerLevel.tick() so custom levels
 * advance their per-world clock to dawn instead of the global clock.
 */
@Mixin(ServerLevel.class)
public class ServerLevelTimeMixin {

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z")
    )
    private boolean redirectSleepWake(ServerClockManager clockManager, Holder<WorldClock> clock,
            ResourceKey<ClockTimeMarker> timeMarkerId) {
        if ((ServerLevel) (Object) this instanceof CustomServerLevel csl) {
            PerWorldClockManager perWorld = csl.getPerWorldClockManager();
            if (perWorld != null) {
                return perWorld.moveToTimeMarker(clock, timeMarkerId);
            }
        }
        return clockManager.moveToTimeMarker(clock, timeMarkerId);
    }
}
