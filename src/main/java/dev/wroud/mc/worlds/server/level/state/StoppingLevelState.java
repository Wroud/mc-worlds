package dev.wroud.mc.worlds.server.level.state;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.abstractions.TeleportTransitionAbstraction;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

public class StoppingLevelState extends LevelState {

  public StoppingLevelState(CustomServerLevel level) {
    super(level);
  }

  @Override
  public String getName() {
    return "Stopping";
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    this.kickPlayers(null);

    if (this.level.getChunkSource().chunkMap.hasWork()) {
      this.level.noSave = false;
      this.level.getChunkSource().deactivateTicketsOnClosing();
      this.level.getChunkSource().tick(() -> true, false);
    } else {
      this.level.setState(StoppedLevelState::new);
    }
  }

  protected void kickPlayers(@Nullable ServerLevel destination) {
    if (this.level.players().isEmpty())
      return;

    if (destination == null) {
      destination = this.level.getServer().overworld();
    }

    var players = new ArrayList<>(this.level.players());

    for (ServerPlayer player : players) {
      player
          .teleport(TeleportTransitionAbstraction.spawnAt(player, destination, TeleportTransition.PLACE_PORTAL_TICKET));
    }
  }
}
