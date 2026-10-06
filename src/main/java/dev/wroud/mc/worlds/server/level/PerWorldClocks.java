package dev.wroud.mc.worlds.server.level;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class PerWorldClocks {
  public static final SavedDataType<ServerClockManager> TYPE = new SavedDataType<>(
      McWorldMod.id("world_clocks"),
      ServerClockManager.TYPE.constructor(),
      ServerClockManager.TYPE.codec(),
      ServerClockManager.TYPE.dataFixType());

  public static ServerClockManager create(ServerLevel level) {
    ServerClockManager clockManager = level.getDataStorage().computeIfAbsent(TYPE);
    ((WorldClockOwner) clockManager).mcworlds$setOwner(level);
    clockManager.init(level.getServer());
    return clockManager;
  }

  public static boolean hasOwnClockViewers(MinecraftServer server) {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      if (player.level().clockManager() != server.clockManager()) {
        return true;
      }
    }
    return false;
  }

  public static void broadcast(PlayerList playerList, ServerClockManager clockManager, Packet<?> packet) {
    for (ServerPlayer player : playerList.getPlayers()) {
      if (player.level().clockManager() == clockManager) {
        player.connection.send(packet);
      }
    }
  }

  public static void broadcastFullSync(MinecraftServer server) {
    sendPerClock(server, ServerClockManager::createFullSyncPacket);
  }

  public static void broadcastTimeSync(MinecraftServer server, Packet<?> sharedPacket) {
    sendPerClock(server, clockManager -> clockManager == server.clockManager()
        ? sharedPacket
        : clockManager.createFullSyncPacket());
  }

  private static void sendPerClock(MinecraftServer server, Function<ServerClockManager, Packet<?>> packetFor) {
    Map<ServerClockManager, Packet<?>> packets = new IdentityHashMap<>();
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      player.connection.send(packets.computeIfAbsent(player.level().clockManager(), packetFor));
    }
  }

  private PerWorldClocks() {
  }
}
