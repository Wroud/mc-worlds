package dev.wroud.mc.worlds.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.tags.DimensionTypeTags;

public class DimensionDetectionUtil {

    public static boolean isEndLikeDimension(Level level) {
        return level.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)
                || level.dimensionTypeRegistration().is(DimensionTypeTags.END_LIKE);
    }

    public static boolean isNetherLikeDimension(Level level) {
        return level.dimensionTypeRegistration().is(BuiltinDimensionTypes.NETHER)
                || level.dimensionTypeRegistration().is(DimensionTypeTags.NETHER_LIKE);
    }

    public static boolean isOverworldLikeDimension(Level level) {
        return level.dimensionTypeRegistration().is(BuiltinDimensionTypes.OVERWORLD)
                || level.dimensionTypeRegistration().is(DimensionTypeTags.OVERWORLD_LIKE);
    }

    public static ResourceKey<Level> getVanillaDimensionMapping(Level level) {
        var mapping = getVanillaDimensionMapping(level.dimensionTypeRegistration());

        if (mapping == null) {
            return level.dimension();
        }

        return mapping;
    }

    public static @Nullable ResourceKey<Level> getVanillaDimensionMapping(Holder<DimensionType> holder) {
        if (holder.is(BuiltinDimensionTypes.END) || holder.is(DimensionTypeTags.END_LIKE)) {
            return Level.END;
        }

        if (holder.is(BuiltinDimensionTypes.NETHER) || holder.is(DimensionTypeTags.NETHER_LIKE)) {
            return Level.NETHER;
        }

        if (holder.is(BuiltinDimensionTypes.OVERWORLD) || holder.is(DimensionTypeTags.OVERWORLD_LIKE)) {
            return Level.OVERWORLD;
        }

        return null;
    }

    public static boolean shouldTreatAsVanillaDimension(Level level, ResourceKey<Level> vanillaDimension) {
        ResourceKey<Level> mappedDimension = getVanillaDimensionMapping(level);
        return mappedDimension != null && mappedDimension.equals(vanillaDimension);
    }
}
