package dev.wroud.mc.worlds.mixin;

import net.minecraft.BlockUtil.FoundRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NetherPortalBlock.class)
public interface NetherPortalBlockAccessor {
    @Invoker("getDimensionTransitionFromExit")
    static TeleportTransition invokeGetDimensionTransitionFromExit(Entity entity, BlockPos blockPos, FoundRectangle foundRectangle, ServerLevel serverLevel, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        throw new AssertionError("Mixin invoker should not be called directly");
    }
}
