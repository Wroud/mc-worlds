package dev.wroud.mc.worlds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.wroud.mc.worlds.command.WorldsCommands;
import dev.wroud.mc.worlds.server.CustomServerLevel;

public class McWorldInitializer implements ModInitializer {

    public static final String MOD_ID = "dev.wroud.mc.worlds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Map<MinecraftServer, McWorld> worlds = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {
            var mcWorld = new McWorld(serverInstance);
            worlds.put(serverInstance, mcWorld);
            mcWorld.loadSavedWorlds();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(serverInstance -> {
            worlds.remove(serverInstance);
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