package dev.wroud.mc.worlds.mixin.groups;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public abstract class RespawnMinecraftServerMixin {

    @WrapOperation(
            method = "setRespawnData",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private void mcworlds$broadcastGroupSpawns(PlayerList playerList, Packet<?> packet, Operation<Void> original) {
        for (ServerPlayer player : playerList.getPlayers()) {
            player.connection.send(WorldGroups.groupOf(player.level()) != null
                    ? new ClientboundSetDefaultSpawnPositionPacket(player.level().getRespawnData())
                    : packet);
        }
    }
}
