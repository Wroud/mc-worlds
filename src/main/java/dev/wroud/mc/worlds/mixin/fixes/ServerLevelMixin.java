package dev.wroud.mc.worlds.mixin.fixes;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @ModifyExpressionValue(
            method = "isAllowedToEnterPortal",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"
            )
    )
    private ResourceKey<Level> modifyLevelDimension(ResourceKey<Level> original) {
        ResourceKey<Level> vanillaDimension = DimensionDetectionUtil.getVanillaDimensionMapping((Level) (Object) this);
        return vanillaDimension != null ? vanillaDimension : original;
    }

    @Inject(method = "isAllowedToEnterPortal", at = @At("RETURN"), cancellable = true)
    private void onIsAllowedToEnterPortal(Level level, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof CustomServerLevel customServerLevel) {
            if (!customServerLevel.canTeleport()) {
                cir.setReturnValue(false);
            }
        }
    }

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/StructureCheck;<init>(Lnet/minecraft/world/level/chunk/storage/ChunkScanAccess;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/biome/BiomeSource;JLcom/mojang/datafixers/DataFixer;)V"
            ),
            index = 3
    )
    private ResourceKey<Level> structureCheckDimension(ResourceKey<Level> dimension) {
        return DimensionDetectionUtil.chunkFixContextDimension((ServerLevel) (Object) this, dimension);
    }
    
}
