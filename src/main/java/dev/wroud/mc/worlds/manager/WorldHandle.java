package dev.wroud.mc.worlds.manager;

import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.resources.Identifier;

public class WorldHandle {
  private Identifier id;
  private CustomServerLevel serverLevel;
  private WorldsLevelData levelData;

  public WorldHandle(Identifier id, WorldsLevelData levelData, CustomServerLevel serverLevel) {
    this.id = id;
    this.levelData = levelData;
    this.serverLevel = serverLevel;
  }

  public Identifier getId() {
    return id;
  }

  public WorldsLevelData getLevelData() {
    return levelData;
  }

  public CustomServerLevel getServerLevel() {
    return serverLevel;
  }
}
