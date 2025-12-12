package dev.wroud.mc.worlds.mixin;

import java.util.Map;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor("executor")
    Executor getExecutor();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();

    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    @Invoker("setupDebugLevel")
    void invokeSetupDebugLevel(WorldData worldData);
}
