package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla suppresses weather in the End solely via `dimension() != Level.END`;
 * the End dimension type itself has skylight and no ceiling, so an END-like
 * custom dimension passes every other term and would rain and thunder.
 */
@Mixin(Level.class)
public class LevelWeatherMixin {

    @ModifyReturnValue(method = "canHaveWeather", at = @At("RETURN"))
    private boolean mcworlds$suppressWeatherInEndLike(boolean original) {
        return original && !DimensionDetectionUtil.isEndLikeDimension((Level) (Object) this);
    }
}
