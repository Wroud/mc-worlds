package dev.wroud.mc.worlds.server.level.state;

import java.util.function.BooleanSupplier;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;

public class StoppedLevelState extends LevelState {

  public StoppedLevelState(CustomServerLevel level) {
    super(level);
  }

  @Override
  public String getName() {
    return "Stopped";
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
  }
}
