package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replaces the global full clock sync packet sent in sendLevelInfo() with
 * the per-world clock states when the player is entering a CustomServerLevel.
 * This covers initial join, dimension teleport, and respawn — all paths that
 * call sendLevelInfo().
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @ModifyExpressionValue(
        method = "sendLevelInfo",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private ServerClockManager mcworlds$perWorldClock(ServerClockManager original, ServerPlayer player, ServerLevel level) {
        return level.clockManager();
    }
}
