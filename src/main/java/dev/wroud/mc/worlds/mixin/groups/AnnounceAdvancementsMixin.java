package dev.wroud.mc.worlds.mixin.groups;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import net.minecraft.network.chat.Component;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerAdvancements.class)
public abstract class AnnounceAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @WrapOperation(
            method = "lambda$award$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
            )
    )
    private void mcworlds$announceToGroup(PlayerList playerList, Component message, boolean overlay,
            Operation<Void> original) {
        WorldGroups.broadcastSystemMessage(playerList, this.player.level(), message, overlay);
    }
}
