package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(LocationPredicate.class)
public class LocationPredicateMixin {
    
    @Shadow 
    @Final 
    private Optional<ResourceKey<Level>> dimension;

    @WrapOperation(
        method = "matches",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"
        )
    )
    private ResourceKey<Level> modifyDimensionCheck(
        ServerLevel serverLevel,
        Operation<ResourceKey<Level>> original
    ) {
        ResourceKey<Level> actualDimension = original.call(serverLevel);
        
        if (dimension.isPresent()) {
            ResourceKey<Level> requiredDimension = dimension.get();
            
            if (DimensionDetectionUtil.shouldTreatAsVanillaDimension(serverLevel, requiredDimension)) {
                return requiredDimension;
            }
        }
        
        return actualDimension;
    }
}
