package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Redirects the sleep-wake time skip in ServerLevel.tick() so custom levels
 * advance their per-world clock to dawn instead of the global clock.
 */
@Mixin(ServerLevel.class)
public class ServerLevelTimeMixin {

    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private ServerClockManager mcworlds$perWorldClock(ServerClockManager original) {
        return ((ServerLevel) (Object) this).clockManager();
    }
}
