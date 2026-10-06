package dev.wroud.mc.worlds.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.tags.DimensionTypeTags;

public class DimensionDetectionUtil {

    public static boolean isEndLikeDimension(Level level) {
        Holder<DimensionType> type = level.dimensionTypeRegistration();
        return type.is(BuiltinDimensionTypes.END) || type.is(DimensionTypeTags.END_LIKE);
    }

    public static boolean isNetherLikeDimension(Level level) {
        Holder<DimensionType> type = level.dimensionTypeRegistration();
        return type.is(BuiltinDimensionTypes.NETHER) || type.is(DimensionTypeTags.NETHER_LIKE);
    }

    public static boolean isOverworldLikeDimension(Level level) {
        Holder<DimensionType> type = level.dimensionTypeRegistration();
        return type.is(BuiltinDimensionTypes.OVERWORLD) || type.is(DimensionTypeTags.OVERWORLD_LIKE);
    }

    public static ResourceKey<Level> getVanillaDimensionMapping(Level level) {
        var mapping = getVanillaDimensionMapping(level.dimensionTypeRegistration());

        if (mapping == null) {
            return level.dimension();
        }

        return mapping;
    }

    public static ResourceKey<Level> getVanillaDimensionMapping(MinecraftServer server, ResourceKey<Level> dimension) {
        var type = getDimensionType(server, dimension);
        var mapping = type != null ? getVanillaDimensionMapping(type) : null;
        return mapping != null ? mapping : dimension;
    }

    private static @Nullable Holder<DimensionType> getDimensionType(MinecraftServer server, ResourceKey<Level> dimension) {
        var loaded = ((MinecraftServerAccessor) server).getLevels().get(dimension);
        if (loaded != null) {
            return loaded.dimensionTypeRegistration();
        }

        return McWorldMod.getMcWorld(server)
            .map(worlds -> worlds.getManager().getWorldsData().getLevelData(dimension.identifier()))
            .map(WorldsLevelData::getLevelStem)
            .map(LevelStem::type)
            .orElse(null);
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
        return getVanillaDimensionMapping(level).equals(vanillaDimension);
    }
}
