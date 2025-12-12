package dev.wroud.mc.worlds;

import dev.wroud.mc.worlds.manager.WorldHandle;
import dev.wroud.mc.worlds.manager.WorldsManager;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

public class McWorld {
  private WorldsManager worldsManager;

  public McWorld(MinecraftServer server) {
    this.worldsManager = new WorldsManager(server);
  }

  public WorldsManager getManager() {
    return worldsManager;
  }

  public void loadSavedWorlds() {
    worldsManager.loadSavedWorlds();
  }

  public WorldHandle loadOrCreate(Identifier location, WorldsLevelData levelData) {
    return worldsManager.loadOrCreateWorld(location, levelData);
  }

  public void handleWorldUnload(Identifier level) {
    worldsManager.unloadWorld(level);
  }
}
