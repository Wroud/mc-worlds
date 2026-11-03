package dev.wroud.mc.worlds.mixin.groups;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeleportTransition.class)
public abstract class RespawnTeleportTransitionMixin {

    @WrapOperation(
            method = {
                    "createDefault",
                    "missingRespawnBlock(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;findRespawnDimension()Lnet/minecraft/server/level/ServerLevel;"
            )
    )
    private static ServerLevel mcworlds$groupRespawnDimension(
            MinecraftServer server,
            Operation<ServerLevel> original,
            ServerPlayer player,
            TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        ServerLevel spawnLevel = server.getLevel(player.level().getRespawnData().dimension());
        return spawnLevel != null ? spawnLevel : original.call(server);
    }
}
