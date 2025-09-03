package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Shadow
    public LocalPlayer player;

    @ModifyExpressionValue(
        method = "getSituationalMusic",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;"
        )
    )
    private ResourceKey<Level> getSituationalMusicEndKey(
        ResourceKey<Level> original
    ) {
        if (this.player != null) {
            Level level = this.player.level();
            ResourceKey<Level> currentDimension = level.dimension();
            
            if (DimensionDetectionUtil.isEndLikeDimension(level)) {
                return currentDimension;
            }
        }

        return original;
    }

    @ModifyExpressionValue(
        method = "getSituationalMusic",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/Level;NETHER:Lnet/minecraft/resources/ResourceKey;"
        )
    )
    private ResourceKey<Level> getSituationalMusicNetherKey(
        ResourceKey<Level> original
    ) {
        if (this.player != null) {
            Level level = this.player.level();
            ResourceKey<Level> currentDimension = level.dimension();
            
            if (DimensionDetectionUtil.isNetherLikeDimension(level)) {
                return currentDimension;
            }
        }

        return original;
    }
}
