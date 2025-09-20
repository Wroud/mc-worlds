package dev.wroud.mc.worlds.abstractions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class ServerPlayerAbstraction {

  public static MinecraftServer getServer(ServerPlayer player) {
    return player.level().getServer();
  }
}
