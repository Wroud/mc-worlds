package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownEnderpearl.class)
public class ThrownEnderpearlMixin {

    /**
     * Modifies the first dimension check: level.dimension() == Level.END
     * Maps custom END-like dimensions to Level.END
     */
    @ModifyExpressionValue(
            method = "canTeleport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 0
            )
    )
    private ResourceKey<Level> modifyFromLevelDimension(
            ResourceKey<Level> original,
            @Local(argsOnly = true, ordinal = 0) Level level
    ) {
        ResourceKey<Level> vanillaDimension = DimensionDetectionUtil.getVanillaDimensionMapping(level);
        return vanillaDimension != null ? vanillaDimension : original;
    }

    /**
     * Modifies the second dimension check: level2.dimension() == Level.OVERWORLD
     * Maps custom OVERWORLD-like dimensions to Level.OVERWORLD
     */
    @ModifyExpressionValue(
            method = "canTeleport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 1
            )
    )
    private ResourceKey<Level> modifyToLevelDimension(
            ResourceKey<Level> original,
            @Local(argsOnly = true, ordinal = 1) Level level2
    ) {
        ResourceKey<Level> vanillaDimension = DimensionDetectionUtil.getVanillaDimensionMapping(level2);
        return vanillaDimension != null ? vanillaDimension : original;
    }
}
