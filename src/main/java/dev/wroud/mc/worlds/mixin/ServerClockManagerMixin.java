package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.server.level.WorldClockOwner;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ServerClockManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerClockManager.class)
public class ServerClockManagerMixin implements WorldClockOwner {

    @Unique
    private @Nullable ServerLevel mcworlds$owner;

    @Override
    public void mcworlds$setOwner(ServerLevel level) {
        this.mcworlds$owner = level;
    }

    @Override
    public @Nullable ServerLevel mcworlds$getOwner() {
        return this.mcworlds$owner;
    }

    @ModifyExpressionValue(
        method = "getGameTime",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel mcworlds$gameTimeSource(ServerLevel original) {
        ServerLevel owner = this.mcworlds$owner;
        return owner != null ? owner : original;
    }

    @WrapOperation(
        method = "modifyClock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void mcworlds$broadcastToOwner(PlayerList playerList, Packet<?> packet, Operation<Void> original) {
        ServerLevel owner = this.mcworlds$owner;
        if (owner != null) {
            playerList.broadcastAll(packet, owner.dimension());
        } else {
            original.call(playerList, packet);
        }
    }
}
