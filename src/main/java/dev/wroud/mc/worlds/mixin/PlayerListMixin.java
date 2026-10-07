package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {

    @ModifyExpressionValue(
        method = "sendLevelInfo",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private ServerClockManager mcworlds$perWorldClock(ServerClockManager original, ServerPlayer player, ServerLevel level) {
        return level.clockManager();
    }

    @Inject(method = "sendLevelInfo", at = @At("TAIL"))
    private void mcworlds$sendClientRules(ServerPlayer player, ServerLevel level, CallbackInfo ci) {
        PerWorldGameRules.sendClientRules(player, level.getGameRules());
    }
}
