package dev.wroud.mc.worlds.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.ContextDimension;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ContextDimension.class)
public class ContextDimensionMixin {

    @ModifyReturnValue(
        method = "get",
        at = @At("RETURN")
    )
    private ResourceKey<Level> modifyDimensionKey(
        ResourceKey<Level> original,
        ItemStack itemStack,
        @Nullable ClientLevel clientLevel,
        @Nullable LivingEntity livingEntity,
        int i,
        ItemDisplayContext itemDisplayContext
    ) {
        if (clientLevel != null && original != null) {
            var mappedDimension = DimensionDetectionUtil.getVanillaDimensionMapping(clientLevel);
            if (mappedDimension != null) {
                return mappedDimension;
            }
        }
        
        return original;
    }
}
