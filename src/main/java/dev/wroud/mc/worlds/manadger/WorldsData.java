package dev.wroud.mc.worlds.manadger;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manadger.level.data.WorldsLevelData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class WorldsData extends SavedData {

  private final Map<ResourceLocation, WorldsLevelData> levels;
  private static final Codec<WorldsData> CODEC = Codec
      .unboundedMap(ResourceLocation.CODEC, WorldsLevelData.CODEC)
      .xmap(WorldsData::new, wd -> wd.levels);

  public static final SavedDataType<WorldsData> TYPE = new SavedDataType<>(McWorldMod.MOD_ID,
      WorldsData::new, CODEC, null);

  private WorldsData() {
    this.levels = new HashMap<>();
  }

  private WorldsData(Map<ResourceLocation, WorldsLevelData> worlds) {
    this.levels = new HashMap<>(worlds);
  }

  @Override
  public boolean isDirty() {
    return true;
  }

  @Override
  public void setDirty(boolean bl) {
    super.setDirty(true);
  }

  public void addLevelData(ResourceLocation id, WorldsLevelData data) {
    levels.put(id, data);
  }

  public boolean removeLevelData(ResourceLocation id) {
    if (levels.remove(id) != null) {
      return true;
    }
    return false;
  }

  public WorldsLevelData getLevelData(ResourceLocation id) {
    return levels.get(id);
  }

  public Map<ResourceLocation, WorldsLevelData> getLevelsData() {
    return new HashMap<>(levels);
  }

}
