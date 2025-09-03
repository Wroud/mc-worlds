package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTriggerMixin {

  @Shadow
  public abstract ServerLevel level();

  @Shadow
  private Vec3 enteredNetherPosition;

  @Inject(
      method = "triggerDimensionChangeTriggers",
      at = @At(value = "INVOKE", 
               target = "Lnet/minecraft/advancements/CriteriaTriggers;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/phys/Vec3;)V",
               shift = At.Shift.AFTER)
  )
  private void handleCustomDimensionNetherTravel(ServerLevel fromLevel, CallbackInfo ci) {
    ServerPlayer player = (ServerPlayer) (Object) this;
    ServerLevel toLevel = level();

    ResourceKey<Level> fromDimension = fromLevel.dimension();
    ResourceKey<Level> toDimension = toLevel.dimension();

    if (fromDimension == Level.OVERWORLD && toDimension == Level.NETHER) {
      return;
    }

    boolean isFromOverworldLike = DimensionDetectionUtil.isOverworldLikeDimension(fromLevel);
    boolean isToNetherLike = DimensionDetectionUtil.isNetherLikeDimension(toLevel);

    if (isFromOverworldLike && isToNetherLike && this.enteredNetherPosition != null) {
      CriteriaTriggers.NETHER_TRAVEL.trigger(player, this.enteredNetherPosition);
    }
  }

  @Redirect(
      method = "triggerDimensionChangeTriggers",
      at = @At(value = "FIELD", 
               target = "Lnet/minecraft/server/level/ServerPlayer;enteredNetherPosition:Lnet/minecraft/world/phys/Vec3;",
               opcode = org.objectweb.asm.Opcodes.PUTFIELD)
  )
  private void redirectNetherPositionClearing(ServerPlayer instance, Vec3 value) {
    ServerLevel currentLevel = level();

    if (!DimensionDetectionUtil.isNetherLikeDimension(currentLevel) && currentLevel.dimension() != Level.NETHER) {
      this.enteredNetherPosition = value;
    }
  }
}
