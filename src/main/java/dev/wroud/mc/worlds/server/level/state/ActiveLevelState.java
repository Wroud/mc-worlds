package dev.wroud.mc.worlds.server.level.state;

import java.util.function.BooleanSupplier;

import dev.wroud.mc.worlds.mixin.ServerLevelAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;

public class ActiveLevelState extends LevelState {

  private int frozenEmptyTime;

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
        && this.emptyTime() > CustomServerLevel.STOP_AFTER
        && !this.level.isRespawnLevel()) {
      this.level.stop(false);
      return;
    }
  }

  private int emptyTime() {
    if (this.level.tickRateManager().runsNormally()) {
      this.frozenEmptyTime = 0;
      return ((ServerLevelAccessor) this.level).getEmptyTime();
    }
    this.frozenEmptyTime = this.level.players().isEmpty() && this.level.getForceLoadedChunks().isEmpty()
        ? this.frozenEmptyTime + 1
        : 0;
    return this.frozenEmptyTime;
  }
}
