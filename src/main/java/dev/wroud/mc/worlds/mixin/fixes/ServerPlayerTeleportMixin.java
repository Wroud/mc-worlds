package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal./*? if >=1.21.2 {*/ TeleportTransition /*?} else {*/ /*DimensionTransition *//*?}*/;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {

  @Shadow
  public abstract ServerLevel level();

  @Shadow
  private Vec3 enteredNetherPosition;

  @Inject(
      method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", 
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V", shift = At.Shift.BEFORE)
  )
  private void handleCustomDimensionNetherEntry(TeleportTransition teleportTransition,
      CallbackInfoReturnable<ServerPlayer> cir) {
    ServerPlayer player = (ServerPlayer) (Object) this;

    ServerLevel fromLevel = level();
    ServerLevel toLevel = teleportTransition.newLevel();

    ResourceKey<Level> fromDimension = fromLevel.dimension();
    ResourceKey<Level> toDimension = toLevel.dimension();

    if (fromDimension == Level.OVERWORLD && toDimension == Level.NETHER) {
      return;
    }

    boolean isFromOverworldLike = DimensionDetectionUtil.isOverworldLikeDimension(fromLevel)
        || fromDimension == Level.OVERWORLD;
    boolean isToNetherLike = DimensionDetectionUtil.isNetherLikeDimension(toLevel) || toDimension == Level.NETHER;

    if (isFromOverworldLike && isToNetherLike) {
      this.enteredNetherPosition = player.position();
    }
  }
}
