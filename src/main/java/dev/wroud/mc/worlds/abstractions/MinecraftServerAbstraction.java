package dev.wroud.mc.worlds.abstractions;

import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.ServerLevelData;

public class MinecraftServerAbstraction {

  public static void initializeLevel(MinecraftServer server, ServerLevel serverLevel) {
    if (serverLevel.getLevelData() instanceof ServerLevelData serverLevelData) {

      if (!serverLevelData.isInitialized()) {
        var debug = server.getWorldData().isDebugWorld();
        var worldOptions = server.getWorldData().worldGenOptions();
        try {
          // TODO: this will block the server thread, should be async, especially slow for
          // spawns in the ocean
          MinecraftServerAccessor.invokeSetInitialSpawn(serverLevel, serverLevelData, worldOptions.generateBonusChest(),
              debug, server.getLevelLoadListener());
          serverLevelData.setInitialized(true);
          if (debug) {
            serverLevelData.setRaining(false);
            serverLevelData.setThundering(false);
            serverLevelData.setClearWeatherTime(1000000000);
            serverLevelData.setDayTime(6000L);
            serverLevelData.setGameType(GameType.SPECTATOR);
          }
        } catch (Throwable var27) {
          CrashReport crashReport = CrashReport.forThrowable(var27, "Exception initializing level");

          try {
            serverLevel.fillReportDetails(crashReport);
          } catch (Throwable var26) {
          }

          throw new ReportedException(crashReport);
        }

        serverLevelData.setInitialized(true);
      }
    }
  }
}
