package dev.wroud.mc.worlds.server.level.state;

import java.util.function.BooleanSupplier;

import dev.wroud.mc.worlds.mixin.ServerLevelAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;

public class ActiveLevelState extends LevelState {

  public ActiveLevelState(CustomServerLevel level) {
    super(level);
  }

  @Override
  public String getName() {
    return "Active";
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    if (!this.level.getServer().isCurrentlySaving()
        && ((ServerLevelAccessor) this.level).getEmptyTime() > CustomServerLevel.STOP_AFTER) {
      this.level.stop(false);
      return;
    }
    this.level.superTick(booleanSupplier);
  }
}
