package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.server.level.PerWorldClocks;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public class MinecraftServerTimeSyncMixin {

    @WrapOperation(
        method = "forceGameTimeSynchronization",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void mcworlds$syncOwnClock(PlayerList playerList, Packet<?> packet, Operation<Void> original) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (PerWorldClocks.hasOwnClockViewers(server)) {
            PerWorldClocks.broadcastTimeSync(server, packet);
        } else {
            original.call(playerList, packet);
        }
    }

    @WrapOperation(
        method = "onGameRuleChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void mcworlds$fullSyncOwnClock(PlayerList playerList, Packet<?> packet, Operation<Void> original) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (PerWorldClocks.hasOwnClockViewers(server)) {
            PerWorldClocks.broadcastFullSync(server);
        } else {
            original.call(playerList, packet);
        }
    }
}
