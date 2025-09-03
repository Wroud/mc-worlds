package dev.wroud.mc.worlds;

import dev.wroud.mc.worlds.manadger.LevelData;
import dev.wroud.mc.worlds.manadger.WorldHandle;
import dev.wroud.mc.worlds.manadger.WorldsManadger;
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

  public void loadWorlds() {
    worldsManadger.loadWorlds();
    worldsManadger.prepareWorlds();
  }

  public WorldHandle create(ResourceLocation location, LevelData levelData) {
    return worldsManadger.loadWorld(location, levelData);
  }

  public boolean remove(ResourceLocation location) {
    return worldsManadger.removeWorld(location);
  }

  public void prepare(WorldHandle handle) {
    worldsManadger.prepareWorld(handle);
  }
}
