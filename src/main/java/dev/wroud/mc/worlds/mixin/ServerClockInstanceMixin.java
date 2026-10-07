package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.server.level.WorldClockOwner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.clock.ServerClockManager$ServerClockInstance")
public class ServerClockInstanceMixin implements WorldClockOwner {

    @Unique
    private @Nullable ServerLevel mcworlds$owner;

    @Override
    public void mcworlds$setOwner(ServerLevel level) {
        this.mcworlds$owner = level;
    }

    @WrapOperation(
        method = "packNetworkState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getGlobalGameRules()Lnet/minecraft/world/level/gamerules/GameRules;"))
    private GameRules mcworlds$ownerGameRules(MinecraftServer server, Operation<GameRules> original) {
        ServerLevel owner = this.mcworlds$owner;
        return owner != null ? owner.getGameRules() : server.getGameRules();
    }
}
