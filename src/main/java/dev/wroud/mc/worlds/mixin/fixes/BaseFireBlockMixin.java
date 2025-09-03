package dev.wroud.mc.worlds.mixin.fixes;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;

@Mixin(BaseFireBlock.class)
public class BaseFireBlockMixin {

    @Inject(method = "inPortalDimension", at = @At("HEAD"), cancellable = true)
    private static void allowPortalsInCustomDimensions(Level level, CallbackInfoReturnable<Boolean> cir) {
        boolean isOverworldLike = DimensionDetectionUtil.isOverworldLikeDimension(level);
        boolean isNetherLike = DimensionDetectionUtil.isNetherLikeDimension(level);
        
        if (isOverworldLike || isNetherLike) {
            cir.setReturnValue(true);
        }
    }
}
