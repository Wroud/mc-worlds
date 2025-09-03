package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {
    @Shadow
    @Final
    public ResourceKey<Level> dimension;

    @ModifyExpressionValue(method = "calculateRotation", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;NETHER:Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<Level> calculateRotationNetherKey(
            ResourceKey<Level> original,
            LevelAccessor levelAccessor,
            double rotation) {
        if (levelAccessor != null) {
            MinecraftServer minecraftServer = levelAccessor.getServer();
            if (minecraftServer != null) {
                ServerLevel level = minecraftServer.getLevel(this.dimension);
                if (level != null) {
                    ResourceKey<Level> mappedDimension = DimensionDetectionUtil.getVanillaDimensionMapping(level);
                    if (mappedDimension != null) {
                        return mappedDimension;
                    }
                }
            }
        }

        return original;
    }
}
