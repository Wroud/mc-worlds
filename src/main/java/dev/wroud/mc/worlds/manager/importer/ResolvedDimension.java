package dev.wroud.mc.worlds.manager.importer;

import java.util.List;

import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;

public record ResolvedDimension(Identifier sourceDim, ResourceKey<Level> newKey, LevelStem stem, WorldsLevelData data,
    List<Component> warnings) {
}
