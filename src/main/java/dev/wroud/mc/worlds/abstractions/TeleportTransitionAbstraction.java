package dev.wroud.mc.worlds.abstractions;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition;
import net.minecraft.world.phys.Vec3;

public class TeleportTransitionAbstraction {

  public static TeleportTransition spawnAtRespawn(Entity entity, ServerLevel destination,
      PostTeleportTransition postTeleportTransition) {
    var respawnData = destination.getRespawnData();
    return new TeleportTransition(destination, findAdjustedSharedSpawnPos(destination, entity), Vec3.ZERO,
        respawnData.yaw(), respawnData.pitch(), false, false, Set.of(), postTeleportTransition);
  }

  public static TeleportTransition spawnAt(Entity entity, ServerLevel destination,
      PostTeleportTransition postTeleportTransition) {
    var respawnData = destination.getLevelData().getRespawnData();
    return new TeleportTransition(destination, adjustSpawnLocation(destination, respawnData.pos()), Vec3.ZERO,
        respawnData.yaw(), respawnData.pitch(), false, false, Set.of(), postTeleportTransition);
  }

  private static Vec3 findAdjustedSharedSpawnPos(ServerLevel serverLevel, Entity entity) {
    return entity.adjustSpawnLocation(serverLevel, serverLevel.getRespawnData().pos()).getBottomCenter();
  }

  private static Vec3 adjustSpawnLocation(ServerLevel serverLevel, BlockPos blockPos) {
    var vec3 = blockPos.getCenter();
    int i = serverLevel.getChunkAt(blockPos).getHeight(Types.MOTION_BLOCKING_NO_LEAVES, blockPos.getX(),
        blockPos.getZ()) + 1;
    return BlockPos.containing(vec3.x, i, vec3.z).getBottomCenter();
  }
}
