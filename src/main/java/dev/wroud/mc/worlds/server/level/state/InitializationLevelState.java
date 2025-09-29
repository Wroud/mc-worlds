package dev.wroud.mc.worlds.server.level.state;

import java.util.function.BooleanSupplier;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;

public class InitializationLevelState extends LevelState {
  private SpawnPreparationHelper spawnPreparationHelper;

  public InitializationLevelState(CustomServerLevel level) {
    super(level);
    this.spawnPreparationHelper = new SpawnPreparationHelper(level);
  }

  @Override
  public String getName() {
    return "Initialization";
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    this.spawnPreparationHelper.tick();
    if (this.spawnPreparationHelper.isFinished()) {
      this.level.setState(ActivationLevelState::new);
    }
  }
}
