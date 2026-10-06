package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.server.level.PerWorldClocks;
import dev.wroud.mc.worlds.server.level.WorldClockOwner;
import java.util.List;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.gamerules.GameRules;
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

    @WrapOperation(
        method = "getGameTime",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel mcworlds$gameTimeSource(MinecraftServer server, Operation<ServerLevel> original) {
        ServerLevel owner = this.mcworlds$owner;
        return owner != null ? owner : original.call(server);
    }

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getGlobalGameRules()Lnet/minecraft/world/level/gamerules/GameRules;"))
    private GameRules mcworlds$ownerGameRules(MinecraftServer server, Operation<GameRules> original) {
        ServerLevel owner = this.mcworlds$owner;
        return owner != null ? owner.getGameRules() : original.call(server);
    }

    @WrapOperation(
        method = "modifyClock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getAllLevels()Ljava/lang/Iterable;"))
    private Iterable<ServerLevel> mcworlds$invalidateOwnerOnly(MinecraftServer server, Operation<Iterable<ServerLevel>> original) {
        ServerLevel owner = this.mcworlds$owner;
        return owner != null ? List.of(owner) : original.call(server);
    }

    @WrapOperation(
        method = "modifyClock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void mcworlds$broadcastToClockViewers(PlayerList playerList, Packet<?> packet, Operation<Void> original) {
        ServerLevel owner = this.mcworlds$owner;
        if (owner == null && !PerWorldClocks.hasOwnClockViewers(playerList.getServer())) {
            original.call(playerList, packet);
        } else {
            PerWorldClocks.broadcast(playerList, (ServerClockManager) (Object) this, packet);
        }
    }
}
