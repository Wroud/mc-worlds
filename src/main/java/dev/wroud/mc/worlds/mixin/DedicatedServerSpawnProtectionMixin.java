package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DedicatedServer.class)
public class DedicatedServerSpawnProtectionMixin {

    @ModifyExpressionValue(
        method = "isUnderSpawnProtection",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/dedicated/DedicatedServer;spawnProtectionRadius()I"))
    private int mcworlds$spawnProtection(int radius, @Local(argsOnly = true) ServerLevel level) {
        return PerWorldGameRules.spawnProtection(level, radius);
    }
}
