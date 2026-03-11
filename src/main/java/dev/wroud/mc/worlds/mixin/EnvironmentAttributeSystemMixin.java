package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.server.level.PerWorldClockManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.level.Level;

/**
 * Redirects the ClockManager captured by EnvironmentAttributeSystem during
 * construction so custom levels use their per-world clock for sky/moon rendering.
 *
 * The redirect falls back to the global clock when perWorldClockManager is null
 * (i.e. during the ServerLevel super() constructor call, before the field is set).
 * CustomServerLevel then rebuilds EnvironmentAttributeSystem with the per-world
 * clock in its own constructor body and overrides environmentAttributes().
 */
@Mixin(EnvironmentAttributeSystem.class)
public class EnvironmentAttributeSystemMixin {

    @Redirect(
        method = "addDefaultLayers(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;Lnet/minecraft/world/level/Level;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;clockManager()Lnet/minecraft/world/clock/ClockManager;")
    )
    private static ClockManager redirectClockManager(Level level) {
        if (level instanceof CustomServerLevel csl) {
            PerWorldClockManager perWorld = csl.getPerWorldClockManager();
            if (perWorld != null) {
                return perWorld;
            }
        }
        return level.clockManager();
    }
}
