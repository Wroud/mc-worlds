package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerGameRulesMixin {

    @ModifyExpressionValue(
        method = "restoreFrom",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getGameRules()Lnet/minecraft/world/level/gamerules/GameRules;"))
    private GameRules mcworlds$deathWorldRules(GameRules original, ServerPlayer oldPlayer, boolean restoreAll) {
        return oldPlayer.level().getGameRules();
    }
}
