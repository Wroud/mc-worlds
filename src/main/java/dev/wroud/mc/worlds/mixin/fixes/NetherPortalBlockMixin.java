package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.manager.group.WorldKind;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.jspecify.annotations.Nullable;
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
        return DimensionDetectionUtil.isNetherLikeDimension(serverLevel) ? Level.OVERWORLD : Level.NETHER;
    }

    @WrapOperation(
            method = "getPortalDestination",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"
            )
    )
    private @Nullable ServerLevel mcworlds$groupDestination(
            MinecraftServer server,
            ResourceKey<Level> key,
            Operation<ServerLevel> original,
            ServerLevel currentLevel,
            Entity entity,
            BlockPos portalEntryPos
    ) {
        WorldKind kind = DimensionDetectionUtil.isNetherLikeDimension(currentLevel) ? WorldKind.OVERWORLD : WorldKind.NETHER;
        return WorldGroups.portalDestination(server, currentLevel, entity, kind, key, target -> original.call(server, target));
    }
}
