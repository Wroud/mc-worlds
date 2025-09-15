package dev.wroud.mc.worlds;

import dev.wroud.mc.worlds.data.tags.DimensionTypeTagsProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.RegistrySetBuilder;

public class DataGenerator implements DataGeneratorEntrypoint {

  @Override
  public void onInitializeDataGenerator(FabricDataGenerator generator) {
    FabricDataGenerator.Pack pack = generator.createPack();

    pack.addProvider(DimensionTypeTagsProvider::new);
  }

  @Override
  public void buildRegistry(RegistrySetBuilder registryBuilder) {
  }
}