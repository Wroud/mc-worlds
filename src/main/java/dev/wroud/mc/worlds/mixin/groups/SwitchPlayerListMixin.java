package dev.wroud.mc.worlds.mixin.groups;

import java.nio.file.Path;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import dev.wroud.mc.worlds.manager.group.transition.GroupTransitions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class SwitchPlayerListMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "respawn", at = @At("HEAD"), cancellable = true)
    private void mcworlds$respawnAcrossGroups(ServerPlayer player, boolean keepAllPlayerData,
            Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayer> cir) {
        // WorldThreader swaps the player on every dimension change by calling respawn with a null reason
        var transitions = reason != null ? GroupTransitions.of(this.server) : null;
        if (transitions == null) {
            return;
        }
        var switched = transitions.respawn(player, keepAllPlayerData, reason);
        if (switched != null) {
            cir.setReturnValue(switched);
        }
    }

    @Inject(method = "getPlayerStats", at = @At("HEAD"))
    private void mcworlds$groupStats(Player player, CallbackInfoReturnable<ServerStatsCounter> cir) {
        var transitions = GroupTransitions.of(this.server);
        if (transitions != null) {
            transitions.prepareStats(player);
        }
    }

    @ModifyExpressionValue(
            method = "getPlayerAdvancements",
            at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"))
    private Path mcworlds$groupAdvancementsFile(Path vanillaFile, @Local(argsOnly = true) ServerPlayer player) {
        var transitions = GroupTransitions.of(this.server);
        return transitions != null ? transitions.advancementsFile(player, vanillaFile) : vanillaFile;
    }

    @WrapOperation(
            method = "save",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/PlayerDataStorage;save(Lnet/minecraft/world/entity/player/Player;)V"))
    private void mcworlds$saveToGroup(PlayerDataStorage storage, Player player, Operation<Void> original) {
        var transitions = GroupTransitions.of(this.server);
        if (transitions == null || !transitions.save((ServerPlayer) player)) {
            original.call(storage, player);
        }
    }
}
