package dev.wroud.mc.worlds.server.level.state;

import java.util.function.BooleanSupplier;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;

public abstract class LevelState {

  protected final CustomServerLevel level;

  public LevelState(CustomServerLevel level) {
    this.level = level;
  }

  public abstract String getName();
  public abstract void tick(BooleanSupplier booleanSupplier);
}