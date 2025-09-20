package dev.wroud.mc.worlds.abstractions;

import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

public class TeleportTransitionAbstraction {

  public static TeleportTransition spawnAt(Entity entity, ServerLevel destination,
      PostTeleportTransition postTeleportTransition) {
    LevelData.RespawnData respawnData = destination.getRespawnData();
    return new TeleportTransition(destination, findAdjustedSharedSpawnPos(destination, entity), Vec3.ZERO,
        respawnData.yaw(), respawnData.pitch(), true, false, Set.of(), postTeleportTransition);
  }

  private static Vec3 findAdjustedSharedSpawnPos(ServerLevel serverLevel, Entity entity) {
    return entity.adjustSpawnLocation(serverLevel, serverLevel.getRespawnData().pos()).getBottomCenter();
  }
}
