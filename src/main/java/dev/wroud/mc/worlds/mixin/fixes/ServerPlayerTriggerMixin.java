package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Maps the two dimension keys triggerDimensionChangeTriggers compares so that
 * NETHER_TRAVEL fires when leaving a NETHER-like dimension for an OVERWORLD-like
 * one, and enteredNetherPosition is only retained while still in a NETHER-like
 * dimension.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerTriggerMixin {

  @ModifyExpressionValue(
      method = "triggerDimensionChangeTriggers",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;",
          ordinal = 0))
  private ResourceKey<Level> mcworlds$mapOldDimension(
      ResourceKey<Level> original,
      @Local(argsOnly = true) ServerLevel oldLevel) {
    ResourceKey<Level> mapped = DimensionDetectionUtil.getVanillaDimensionMapping(oldLevel);
    return mapped != null ? mapped : original;
  }

  @ModifyExpressionValue(
      method = "triggerDimensionChangeTriggers",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;",
          ordinal = 1))
  private ResourceKey<Level> mcworlds$mapNewDimension(ResourceKey<Level> original) {
    ResourceKey<Level> mapped = DimensionDetectionUtil
        .getVanillaDimensionMapping(((ServerPlayer) (Object) this).level());
    return mapped != null ? mapped : original;
  }
}
