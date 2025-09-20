package dev.wroud.mc.worlds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.wroud.mc.worlds.command.WorldsCommands;
import dev.wroud.mc.worlds.core.registries.WorldsRegistries;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;

public class McWorldMod implements ModInitializer {

    public static final String MOD_ID = "mc-worlds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Map<MinecraftServer, McWorld> worlds = new HashMap<>();

    @Override
    public void onInitialize() {
        WorldsRegistries.bootstrap();

        ServerWorldEvents.LOAD.register((serverInstance, world) -> {
            if (world.dimension() == Level.OVERWORLD && !worlds.containsKey(serverInstance)) {
                var mcWorld = new McWorld(serverInstance);
                worlds.put(serverInstance, mcWorld);
                mcWorld.loadSavedWorlds();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(serverInstance -> {
            worlds.remove(serverInstance);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var toRemove = new ArrayList<ResourceKey<Level>>();

            for (var entry : ((MinecraftServerAccessor) server).getLevels().entrySet()) {
                var level = entry.getValue();
                if (level instanceof CustomServerLevel customLevel && customLevel.isMarkedForClose()) {
                    toRemove.add(entry.getKey());
                }
            }

            for (var dimensionKey : toRemove) {
                var level = ((MinecraftServerAccessor) server).getLevels().remove(dimensionKey);
                if (level instanceof CustomServerLevel customLevel) {
                    LOGGER.info("Saving chunks for level '{}'/{}", customLevel, customLevel.dimension().location());
                    customLevel.save(null, true, customLevel.noSave);

                    try {
                        customLevel.close();
                        ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, customLevel);
                    } catch (IOException e) {
                        LOGGER.error("Exception closing the level", e);
                    }
                }
            }
        });

        ServerWorldEvents.UNLOAD.register((server, level) -> {
            if (level instanceof CustomServerLevel) {
                worlds.get(server).handleWorldUnload(level.dimension().location());
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, commandBuildContext, environment) -> {
            WorldsCommands.register(dispatcher, commandBuildContext);
        });
    }

    public static MinecraftServer getServer() {
        return worlds.keySet().stream().findFirst().orElse(null);
    }

    public static McWorld getMcWorld(MinecraftServer server) {
        return worlds.get(server);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}