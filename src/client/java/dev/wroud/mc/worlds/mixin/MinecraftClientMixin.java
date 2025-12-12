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

    /**
     * Modifies the End dimension check in getSituationalMusic to also match End-like dimensions.
     * This allows custom dimensions with End-like properties to play the End boss music
     * when the dragon boss bar is showing.
     */
    @ModifyExpressionValue(
        method = "getSituationalMusic",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/Level;END:Lnet/minecraft/resources/ResourceKey;"
        )
    )
    private ResourceKey<Level> getSituationalMusicEndKey(ResourceKey<Level> original) {
        if (this.player != null) {
            Level level = this.player.level();
            
            if (DimensionDetectionUtil.isEndLikeDimension(level)) {
                return level.dimension();
            }
        }

        return original;
    }
}
