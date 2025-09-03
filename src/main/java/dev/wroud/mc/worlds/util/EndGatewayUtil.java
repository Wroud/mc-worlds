package dev.wroud.mc.worlds.util;

import dev.wroud.mc.worlds.mixin.TheEndGatewayBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;

public class EndGatewayUtil {
    
    public static BlockPos findOrCreateValidTeleportPos(ServerLevel serverLevel, BlockPos blockPos) {
        return TheEndGatewayBlockEntityAccessor.invokeFindOrCreateValidTeleportPos(serverLevel, blockPos);
    }
    
    public static void spawnGatewayPortal(ServerLevel serverLevel, BlockPos blockPos, EndGatewayConfiguration endGatewayConfiguration) {
        TheEndGatewayBlockEntityAccessor.invokeSpawnGatewayPortal(serverLevel, blockPos, endGatewayConfiguration);
    }

    public static BlockPos findExitPosition(ServerLevel serverLevel, BlockPos blockPos) {
        return TheEndGatewayBlockEntityAccessor.invokeFindExitPosition(serverLevel, blockPos);
    }
}
