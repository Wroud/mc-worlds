package dev.wroud.mc.worlds.data.tags;

import java.util.concurrent.CompletableFuture;

import dev.wroud.mc.worlds.tags.DimensionTypeTags;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;

public class DimensionTypeTagsProvider extends FabricTagsProvider<DimensionType> {
  public DimensionTypeTagsProvider(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> completableFuture) {
    super(packOutput, Registries.DIMENSION_TYPE, completableFuture);
  }

  @Override
  protected void addTags(HolderLookup.Provider provider) {
    this.builder(DimensionTypeTags.OVERWORLD_LIKE).add(BuiltinDimensionTypes.OVERWORLD);
    this.builder(DimensionTypeTags.NETHER_LIKE).add(BuiltinDimensionTypes.NETHER);
    this.builder(DimensionTypeTags.END_LIKE).add(BuiltinDimensionTypes.END);
  }
}
