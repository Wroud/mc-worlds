package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @ModifyExpressionValue(
            method = "getPortalDestination",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 0
            )
    )
    private ResourceKey<Level> modifyServerLevelDimension(
            ResourceKey<Level> original,
            ServerLevel serverLevel
    ) {
        ResourceKey<Level> vanillaDimension = DimensionDetectionUtil.getVanillaDimensionMapping(serverLevel);
        return vanillaDimension != null ? vanillaDimension : original;
    }

    @ModifyExpressionValue(
            method = "getPortalDestination",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 1
            )
    )
    private ResourceKey<Level> modifyServerLevel2Dimension(
            ResourceKey<Level> original,
            ServerLevel serverLevel
    ) {
        ResourceKey<Level> resourceKey = DimensionDetectionUtil.isNetherLikeDimension(serverLevel) ? Level.OVERWORLD : Level.NETHER;
        ServerLevel serverLevel2 = serverLevel.getServer().getLevel(resourceKey);
        ResourceKey<Level> vanillaDimension = DimensionDetectionUtil.getVanillaDimensionMapping(serverLevel2);
        return vanillaDimension != null ? vanillaDimension : original;
    }
}
