package dev.wroud.mc.worlds.manadger;

import dev.wroud.mc.worlds.manadger.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.resources.ResourceLocation;

public class WorldHandle {
  private ResourceLocation id;
  private CustomServerLevel serverLevel;
  private WorldsLevelData levelData;

  public WorldHandle(ResourceLocation id, WorldsLevelData levelData, CustomServerLevel serverLevel) {
    this.id = id;
    this.levelData = levelData;
    this.serverLevel = serverLevel;
  }

  public ResourceLocation getId() {
    return id;
  }

  public WorldsLevelData getLevelData() {
    return levelData;
  }

  public CustomServerLevel getServerLevel() {
    return serverLevel;
  }
}
