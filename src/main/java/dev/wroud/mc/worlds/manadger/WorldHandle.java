package dev.wroud.mc.worlds.manadger;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public class WorldHandle {
  private ResourceLocation id;
  private ServerLevel serverLevel;
  private LevelData levelData;

  public WorldHandle(ResourceLocation id, LevelData levelData, ServerLevel serverLevel) {
    this.id = id;
    this.levelData = levelData;
    this.serverLevel = serverLevel;
  }

  public ResourceLocation getId() {
    return id;
  }

  public LevelData getLevelData() {
    return levelData;
  }

  public ServerLevel getServerLevel() {
    return serverLevel;
  }
}
