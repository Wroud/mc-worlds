package dev.wroud.mc.worlds.server.level.state;

import java.util.function.BooleanSupplier;

import dev.wroud.mc.worlds.manager.WorldsManager;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.world.level.TicketStorage;

public class ActivationLevelState extends LevelState {
  private boolean ticketsActivated;

  public ActivationLevelState(CustomServerLevel level) {
    super(level);
    this.ticketsActivated = false;
  }

  @Override
  public String getName() {
    return "Activation";
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    if (!this.ticketsActivated) {
      TicketStorage ticketStorage = this.level.getDataStorage().get(TicketStorage.TYPE);
      if (ticketStorage != null) {
        ticketStorage.activateAllDeactivatedTickets();
      }
      this.ticketsActivated = true;
    } else {
      this.level.setSpawnSettings(((MinecraftServerAccessor) this.level.getServer()).invokeSpawningMonsters());
      WorldsManager.LOGGER.info("World prepared: {}", this.level.dimension().location());
      this.level.setState(ActiveLevelState::new);
    }
  }
}
