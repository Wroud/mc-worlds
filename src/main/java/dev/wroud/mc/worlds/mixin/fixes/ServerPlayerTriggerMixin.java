package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public class ServerPlayerTriggerMixin {

  @WrapOperation(
      method = "triggerDimensionChangeTriggers",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"))
  private ResourceKey<Level> mcworlds$mapDimension(ServerLevel level, Operation<ResourceKey<Level>> original) {
    return DimensionDetectionUtil.getVanillaDimensionMapping(level);
  }
}
