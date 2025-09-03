package dev.wroud.mc.worlds.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
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

    public static @Nullable ResourceKey<Level> getVanillaDimensionMapping(Level level) {
        if (isEndLikeDimension(level)) {
            return Level.END;
        }
        
        if (isNetherLikeDimension(level)) {
            return Level.NETHER;
        }
        
        if (isOverworldLikeDimension(level)) {
            return Level.OVERWORLD;
        }
        
        return null;
    }

    public static boolean shouldTreatAsVanillaDimension(Level level, ResourceKey<Level> vanillaDimension) {
        ResourceKey<Level> mappedDimension = getVanillaDimensionMapping(level);
        return mappedDimension != null && mappedDimension.equals(vanillaDimension);
    }
}
