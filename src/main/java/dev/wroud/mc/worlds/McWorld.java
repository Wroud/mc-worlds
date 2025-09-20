package dev.wroud.mc.worlds;

import dev.wroud.mc.worlds.manadger.WorldHandle;
import dev.wroud.mc.worlds.manadger.WorldsManadger;
import dev.wroud.mc.worlds.manadger.level.data.WorldsLevelData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public class McWorld {
  private WorldsManadger worldsManadger;

  public McWorld(MinecraftServer server) {
    this.worldsManadger = new WorldsManadger(server);
  }

  public WorldsManadger getManadger() {
    return worldsManadger;
  }

  public void loadSavedWorlds() {
    worldsManadger.loadSavedWorlds();
  }

  public WorldHandle loadOrCreate(ResourceLocation location, WorldsLevelData levelData) {
    return worldsManadger.loadOrCreateWorld(location, levelData);
  }

  public void handleWorldUnload(ResourceLocation level) {
    worldsManadger.unloadWorld(level);
  }
}
