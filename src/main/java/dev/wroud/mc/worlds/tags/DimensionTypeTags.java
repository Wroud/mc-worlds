package dev.wroud.mc.worlds.tags;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.dimension.DimensionType;

public class DimensionTypeTags {
  public static final TagKey<DimensionType> OVERWORLD_LIKE = create("overworld_like");
  public static final TagKey<DimensionType> NETHER_LIKE = create("nether_like");
  public static final TagKey<DimensionType> END_LIKE = create("end_like");

  private static TagKey<DimensionType> create(String string) {
    return TagKey.create(Registries.DIMENSION_TYPE, McWorldMod.id(string));
  }
}
