package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(ThrownEnderpearl.class)
public class ThrownEnderpearlMixin {

    @ModifyExpressionValue(
            method = "canTeleport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"
            )
    )
    private ResourceKey<Level> modifyServerLevelDimension(
            ResourceKey<Level> original,
            Level serverLevel
    ) {
        ResourceKey<Level> vanillaDimension = DimensionDetectionUtil.getVanillaDimensionMapping(serverLevel);
        return vanillaDimension != null ? vanillaDimension : original;
    }
}
