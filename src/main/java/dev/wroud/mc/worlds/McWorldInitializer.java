package dev.wroud.mc.worlds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.wroud.mc.worlds.command.WorldsCommands;

public class McWorldInitializer implements ModInitializer {

    public static final String MOD_ID = "dev.wroud.mc.worlds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;
    private static McWorld mcWorld;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {
            server = serverInstance;
            mcWorld = new McWorld(serverInstance);
            mcWorld.loadWorlds();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(serverInstance -> {
            server = null;
            mcWorld = null;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, commandBuildContext, environment) -> {
            WorldsCommands.register(dispatcher, commandBuildContext);
        });
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static McWorld getMcWorld() {
        return mcWorld;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}