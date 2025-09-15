package dev.wroud.mc.worlds.data.tags;

import java.util.concurrent.CompletableFuture;

import dev.wroud.mc.worlds.tags.DimensionTypeTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.KeyTagProvider;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;

public class DimensionTypeTagsProvider extends KeyTagProvider<DimensionType> {
  public DimensionTypeTagsProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> completableFuture) {
    super(packOutput, Registries.DIMENSION_TYPE, completableFuture);
  }

  @Override
  protected void addTags(HolderLookup.Provider provider) {
    this.tag(DimensionTypeTags.OVERWORLD_LIKE).add(BuiltinDimensionTypes.OVERWORLD);
    this.tag(DimensionTypeTags.NETHER_LIKE).add(BuiltinDimensionTypes.NETHER);
    this.tag(DimensionTypeTags.END_LIKE).add(BuiltinDimensionTypes.END);

  }
}
