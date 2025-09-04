package dev.wroud.mc.worlds.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;

import org.jetbrains.annotations.Nullable;

public class DimensionDetectionUtil {

    public static boolean isEndLikeDimension(Level level) {
        return level.dimensionTypeRegistration().is(BuiltinDimensionTypes.END);
    }

    public static boolean isNetherLikeDimension(Level level) {
        return level.dimensionTypeRegistration().is(BuiltinDimensionTypes.NETHER);
    }

    public static boolean isOverworldLikeDimension(Level level) {
        return level.dimensionTypeRegistration().is(BuiltinDimensionTypes.OVERWORLD);
    }

    public static ResourceKey<Level> getVanillaDimensionMapping(Level level) {
        var mapping = getVanillaDimensionMapping(level.dimensionTypeRegistration().unwrapKey().orElse(null));

        if (mapping == null) {
            return level.dimension();
        }

        return mapping;
    }

    public static @Nullable ResourceKey<Level> getVanillaDimensionMapping(ResourceKey<DimensionType> type) {
        if (type.equals(BuiltinDimensionTypes.END)) {
            return Level.END;
        }

        if (type.equals(BuiltinDimensionTypes.NETHER)) {
            return Level.NETHER;
        }

        if (type.equals(BuiltinDimensionTypes.OVERWORLD)) {
            return Level.OVERWORLD;
        }

        return null;
    }

    public static boolean shouldTreatAsVanillaDimension(Level level, ResourceKey<Level> vanillaDimension) {
        ResourceKey<Level> mappedDimension = getVanillaDimensionMapping(level);
        return mappedDimension != null && mappedDimension.equals(vanillaDimension);
    }
}
