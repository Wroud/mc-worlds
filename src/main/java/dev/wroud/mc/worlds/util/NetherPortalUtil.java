package dev.wroud.mc.worlds.util;

import dev.wroud.mc.worlds.mixin.NetherPortalBlockAccessor;
import net.minecraft.util.BlockUtil.FoundRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;

public class NetherPortalUtil {
    
    public static TeleportTransition getDimensionTransitionFromExit(Entity entity, BlockPos blockPos, FoundRectangle foundRectangle, ServerLevel serverLevel, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        return NetherPortalBlockAccessor.invokeGetDimensionTransitionFromExit(entity, blockPos, foundRectangle, serverLevel, postTeleportTransition);
    }
}
