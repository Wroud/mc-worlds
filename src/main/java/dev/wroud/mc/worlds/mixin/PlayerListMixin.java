package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.server.level.PerWorldClockManager;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ServerClockManager;

/**
 * Replaces the global full clock sync packet sent in sendLevelInfo() with
 * the per-world clock states when the player is entering a CustomServerLevel.
 * This covers initial join, dimension teleport, and respawn — all paths that
 * call sendLevelInfo().
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Redirect(
        method = "sendLevelInfo",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/clock/ServerClockManager;createFullSyncPacket()Lnet/minecraft/network/protocol/game/ClientboundSetTimePacket;")
    )
    private ClientboundSetTimePacket redirectCreateFullSyncPacket(
            ServerClockManager clockManager,
            ServerPlayer player,
            ServerLevel level) {
        if (level instanceof CustomServerLevel csl) {
            PerWorldClockManager mgr = csl.getPerWorldClockManager();
            if (mgr != null) {
                return mgr.createFullSyncPacket();
            }
        }
        return clockManager.createFullSyncPacket();
    }
}
