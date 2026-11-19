package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.wroud.mc.worlds.manager.group.transition.GroupTransitions;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

  @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
  private void onTeleport(TeleportTransition teleportTransition, CallbackInfoReturnable<ServerPlayer> cir) {
    var level = teleportTransition.newLevel();
    var player = (ServerPlayer) (Object) this;
    if (!CustomServerLevel.admits(level, player)) {
      cir.setReturnValue(null);
      return;
    }
    var transitions = GroupTransitions.of(level.getServer());
    if (transitions != null && transitions.queue(player, teleportTransition)) {
      cir.setReturnValue(teleportTransition.asPassenger() ? null : player);
    }
  }
}
