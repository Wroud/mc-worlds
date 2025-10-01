package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {

    @ModifyExpressionValue(
            method = "getPortalDestination",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 0
            )
    )
    private ResourceKey<Level> modifyFirstEndDimension(
            ResourceKey<Level> original,
            ServerLevel serverLevel
    ) {
        if (DimensionDetectionUtil.isEndLikeDimension(serverLevel)) {
            return serverLevel.dimension();
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "getPortalDestination",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 1
            )
    )
    private ResourceKey<Level> modifySecondEndDimension(
            ResourceKey<Level> original,
            ServerLevel serverLevel
    ) {
        if (DimensionDetectionUtil.isEndLikeDimension(serverLevel)) {
            return serverLevel.dimension();
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "entityInside",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;"
            )
    )
    private ResourceKey<Level> modifyEntityInsideEndDimension(
            ResourceKey<Level> original,
            BlockState blockState,
            Level level,
            BlockPos blockPos,
            Entity entity,
            InsideBlockEffectApplier insideBlockEffectApplier
    ) {
        if (DimensionDetectionUtil.isEndLikeDimension(level)) {
            return level.dimension();
        }
        return original;
    }
}