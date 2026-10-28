package dev.wroud.mc.worlds.manager.importer;

import com.mojang.serialization.Dynamic;

import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.SavedDataStorage;

public record StagedImport(LevelStorageSource.LevelStorageAccess access, Dynamic<?> levelTag,
    SavedDataStorage rootData) {
}
