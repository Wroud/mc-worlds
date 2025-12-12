package dev.wroud.mc.worlds.mixin.fixes;

import com.mojang.datafixers.DataFixer;
import dev.wroud.mc.worlds.manager.WorldsData;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.LegacyTagFixer;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LegacyStructureDataHandler.class)
public class LegacyStructureDataHandlerMixin {

  @Inject(method = "getLegacyTagFixer(Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Supplier;Lcom/mojang/datafixers/DataFixer;)Ljava/util/function/Supplier;", at = @At("HEAD"), cancellable = true)
  private static void redirectCustomDimension(ResourceKey<Level> resourceKey, Supplier<DimensionDataStorage> supplier,
      DataFixer dataFixer, CallbackInfoReturnable<Supplier<LegacyTagFixer>> cir) {
    // Only handle non-vanilla dimensions
    if (resourceKey == Level.OVERWORLD || resourceKey == Level.NETHER || resourceKey == Level.END) {
      return;
    }

    // Return a supplier that lazily transforms the resourceKey and delegates to the original method
    Supplier<LegacyTagFixer> customSupplier = () -> {
      var worldsData = supplier.get().computeIfAbsent(WorldsData.TYPE);
      var levelData = worldsData.getLevelData(resourceKey.identifier());

      if (levelData != null) {
        var mappedDimension = DimensionDetectionUtil.getVanillaDimensionMapping(levelData.getLevelStem().type());
        if (mappedDimension != null) {
          // Call the original method with the mapped dimension and get the fixer
          return LegacyStructureDataHandler.getLegacyTagFixer(mappedDimension, supplier, dataFixer).get();
        }
      }

      // If no mapping found, return empty fixer
      return LegacyTagFixer.EMPTY.get();
    };
    cir.setReturnValue(customSupplier);
  }
}
