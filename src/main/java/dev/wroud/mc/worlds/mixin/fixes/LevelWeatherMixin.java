package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public class LevelWeatherMixin {

    @ModifyExpressionValue(
            method = "canHaveWeather",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<Level> mcworlds$mapDimension(ResourceKey<Level> original) {
        return DimensionDetectionUtil.getVanillaDimensionMapping((Level) (Object) this);
    }
}
