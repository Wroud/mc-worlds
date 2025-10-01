package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallingBlockEntity.class)
public class FallingBlockEntityMixin {

    @ModifyExpressionValue(
            method = "teleport",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 0
            )
    )
    private ResourceKey<Level> modifyFirstEndDimensionCheck(
            ResourceKey<Level> original
    ) {
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        if (DimensionDetectionUtil.isEndLikeDimension(entity.level())) {
            return entity.level().dimension();
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "teleport",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 1
            )
    )
    private ResourceKey<Level> modifySecondEndDimensionCheck(
            ResourceKey<Level> original,
            TeleportTransition teleportTransition
    ) {
        if (DimensionDetectionUtil.isEndLikeDimension(teleportTransition.newLevel())) {
            return teleportTransition.newLevel().dimension();
        }
        return original;
    }
}
