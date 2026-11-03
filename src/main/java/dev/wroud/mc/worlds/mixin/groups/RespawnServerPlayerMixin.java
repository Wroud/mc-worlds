package dev.wroud.mc.worlds.mixin.groups;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class RespawnServerPlayerMixin {

    @WrapOperation(
            method = "findRespawnPositionAndUseSpawnBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"
            )
    )
    private @Nullable ServerLevel mcworlds$respawnInGroup(MinecraftServer server, ResourceKey<Level> key,
            Operation<ServerLevel> original) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        WorldGroups groups = WorldGroups.of(server);
        return groups == null || groups.sameGroup(self.level(), key) ? original.call(server, key) : null;
    }
}
