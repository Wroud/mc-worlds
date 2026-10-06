package dev.wroud.mc.worlds.server.level.state;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

import dev.wroud.mc.worlds.abstractions.TeleportTransitionAbstraction;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

public class StoppingLevelState extends LevelState {
  private boolean kickScheduled;

  public StoppingLevelState(CustomServerLevel level) {
    super(level);
  }

  @Override
  public String getName() {
    return "Stopping";
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    if (!this.level.players().isEmpty() && !this.kickScheduled) {
      this.kickScheduled = true;
      this.level.getServer().execute(this::kickPlayers);
    }

    if (this.level.getChunkSource().chunkMap.hasWork()) {
      this.level.noSave = false;
      this.level.getChunkSource().deactivateTicketsOnClosing();
      this.level.getChunkSource().tick(() -> true, false);
    } else if (this.level.players().isEmpty()) {
      this.level.setState(StoppedLevelState::new);
    }
  }

  private void kickPlayers() {
    this.kickScheduled = false;
    var server = this.level.getServer();
    var destination = server.findRespawnDimension();
    if (destination == this.level) {
      destination = server.overworld();
    }

    var players = new ArrayList<>(this.level.players());

    for (ServerPlayer player : players) {
      player.teleport(TeleportTransitionAbstraction.spawnAtRespawn(player, destination, TeleportTransition.PLACE_PORTAL_TICKET));
    }
  }
}
