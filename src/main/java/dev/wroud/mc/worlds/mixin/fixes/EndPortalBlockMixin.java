package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
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
}