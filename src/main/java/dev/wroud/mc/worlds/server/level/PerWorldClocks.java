package dev.wroud.mc.worlds.server.level;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class PerWorldClocks {
  public static final SavedDataType<ServerClockManager> TYPE = new SavedDataType<>(
      McWorldMod.id("world_clocks"),
      ServerClockManager.TYPE.constructor(),
      ServerClockManager.TYPE.codec(),
      ServerClockManager.TYPE.dataFixType());

  public static ServerClockManager create(ServerLevel level) {
    ServerClockManager clockManager = level.getDataStorage().computeIfAbsent(TYPE);
    ((WorldClockOwner) clockManager).mcworlds$setOwner(level);
    clockManager.init(level.getServer());
    return clockManager;
  }

  private PerWorldClocks() {
  }
}
