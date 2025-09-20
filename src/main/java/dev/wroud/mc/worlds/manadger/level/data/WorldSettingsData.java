package dev.wroud.mc.worlds.manadger.level.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.storage.LevelData.RespawnData;

public class WorldSettingsData {
  public static final Codec<WorldSettingsData> CODEC = RecordCodecBuilder.create(instance -> instance
      .group(
          RespawnData.CODEC.optionalFieldOf("respawn", RespawnData.DEFAULT).forGetter(wsd -> wsd.respawn),
          Codec.BOOL.optionalFieldOf("initialized", false).forGetter(gsd -> gsd.initialized))
      .apply(instance, WorldSettingsData::new));

  public RespawnData respawn;
  public boolean initialized;

  public WorldSettingsData() {
    this.respawn = RespawnData.DEFAULT;
    this.initialized = false;
  }

  public WorldSettingsData(RespawnData respawn, boolean initialized) {
    this.respawn = respawn;
    this.initialized = initialized;
  }
}
