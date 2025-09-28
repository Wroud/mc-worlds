package dev.wroud.mc.worlds.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;

@Mixin(Entity.class)
public class EntityMixin {

  @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), cancellable = true)
  private void onTeleport(TeleportTransition teleportTransition, CallbackInfoReturnable<Entity> cir) {
    var level = teleportTransition.newLevel();
    if (level instanceof CustomServerLevel customServerLevel) {
      if (customServerLevel.isManuallyStopped()) {
        cir.setReturnValue(null);
      }
    }
  }
}
