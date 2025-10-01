package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(Bee.class)
public class BeeMixin {

  @ModifyExpressionValue(method = "isNightOrRaining", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;", ordinal = 0))
  private static ResourceKey<Level> modifyLevelDimension(
      ResourceKey<Level> original,
      Level level) {
    ResourceKey<Level> vanillaDimension = DimensionDetectionUtil.getVanillaDimensionMapping(level);
    return vanillaDimension != null ? vanillaDimension : original;
  }
}
