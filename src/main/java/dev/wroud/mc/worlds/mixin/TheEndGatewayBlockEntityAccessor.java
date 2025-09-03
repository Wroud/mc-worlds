package dev.wroud.mc.worlds.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TheEndGatewayBlockEntity.class)
public interface TheEndGatewayBlockEntityAccessor {
    
    @Invoker("findOrCreateValidTeleportPos")
    static BlockPos invokeFindOrCreateValidTeleportPos(ServerLevel serverLevel, BlockPos blockPos) {
        throw new AssertionError("Mixin invoker should not be called directly");
    }
    
    @Invoker("spawnGatewayPortal")
    static void invokeSpawnGatewayPortal(ServerLevel serverLevel, BlockPos blockPos, EndGatewayConfiguration endGatewayConfiguration) {
        throw new AssertionError("Mixin invoker should not be called directly");
    }

    @Invoker("findExitPosition")
    static BlockPos invokeFindExitPosition(Level level, BlockPos blockPos) {
        throw new AssertionError("Mixin invoker should not be called directly");
    }
}
