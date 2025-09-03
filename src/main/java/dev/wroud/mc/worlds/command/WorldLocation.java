package dev.wroud.mc.worlds.command;

import net.minecraft.BlockUtil;
import net.minecraft.BlockUtil.FoundRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import dev.wroud.mc.worlds.util.EndGatewayUtil;
import dev.wroud.mc.worlds.util.NetherPortalUtil;

public record WorldLocation(ServerLevel level, TeleportTransition transition) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static WorldLocation findSpawn(ServerLevel level, Entity entity) {
        if (DimensionDetectionUtil.isEndLikeDimension(level)) {
            return new WorldLocation(level, getEndSpawn(level, entity));
        } else if (DimensionDetectionUtil.isNetherLikeDimension(level)) {
            return new WorldLocation(level, getNetherSpawn(level, entity));
        }
        Vec3 vec3 = entity.adjustSpawnLocation(level, level.getSharedSpawnPos()).getBottomCenter();
        return new WorldLocation(level, new TeleportTransition(level, vec3, Vec3.ZERO, 0.0F, 0.0F,
                Relative.union(Relative.DELTA, Relative.ROTATION), TeleportTransition.PLACE_PORTAL_TICKET));
    }

    private static TeleportTransition getEndSpawn(ServerLevel level, Entity entity) {
        BlockPos blockPos = entity.blockPosition();
        BlockPos blockPos2 = EndGatewayUtil.findOrCreateValidTeleportPos(level, blockPos);
        blockPos2 = blockPos2.above(10);
        LOGGER.debug("Creating portal at {}", blockPos2);
        EndGatewayUtil.spawnGatewayPortal(level, blockPos2, EndGatewayConfiguration.knownExit(blockPos, false));

        blockPos2 = EndGatewayUtil.findExitPosition(level, blockPos2);
        Vec3 vec3 = blockPos2.getBottomCenter();
        return new TeleportTransition(
                level, vec3, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION),
                TeleportTransition.PLACE_PORTAL_TICKET);
    }

    private static TeleportTransition getNetherSpawn(ServerLevel serverLevel2, Entity entity) {
        BlockPos blockPos = entity.blockPosition();
        WorldBorder worldBorder = serverLevel2.getWorldBorder();
        double d = DimensionType.getTeleportationScale(entity.level().dimensionType(), serverLevel2.dimensionType());
        BlockPos blockPos2 = worldBorder.clampToBounds(entity.getX() * d, entity.getY(), entity.getZ() * d);
        return getExitPortal(serverLevel2, entity, blockPos, blockPos2, true, worldBorder);
    }

    @Nullable
    private static TeleportTransition getExitPortal(ServerLevel serverLevel, Entity entity, BlockPos blockPos,
            BlockPos blockPos2, boolean bl, WorldBorder worldBorder) {
        Optional<BlockPos> optional = serverLevel.getPortalForcer().findClosestPortalPosition(blockPos2, bl,
                worldBorder);
        FoundRectangle foundRectangle;
        TeleportTransition.PostTeleportTransition postTeleportTransition;
        if (optional.isPresent()) {
            BlockPos blockPos3 = (BlockPos) optional.get();
            BlockState blockState = serverLevel.getBlockState(blockPos3);
            foundRectangle = BlockUtil.getLargestRectangleAround(
                    blockPos3, blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS), 21, Axis.Y, 21,
                    blockPosx -> serverLevel.getBlockState(blockPosx) == blockState);
            postTeleportTransition = TeleportTransition.PLAY_PORTAL_SOUND
                    .then(entityx -> entityx.placePortalTicket(blockPos3));
        } else {
            Axis axis = (Axis) entity.level().getBlockState(blockPos).getOptionalValue(NetherPortalBlock.AXIS)
                    .orElse(Axis.X);
            Optional<FoundRectangle> optional2 = serverLevel.getPortalForcer().createPortal(blockPos2, axis);
            if (optional2.isEmpty()) {
                LOGGER.error("Unable to create a portal, likely target out of worldborder");
                return null;
            }

            foundRectangle = (FoundRectangle) optional2.get();
            postTeleportTransition = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET);
        }

        return NetherPortalUtil.getDimensionTransitionFromExit(entity, blockPos, foundRectangle, serverLevel,
                postTeleportTransition);
    }

    public void teleport(Entity entity) {
        entity.teleport(transition);
    }
}
