package dev.wroud.mc.worlds.server.level;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.wroud.mc.worlds.abstractions.TeleportTransitionAbstraction;
import dev.wroud.mc.worlds.manager.WorldsManager;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.mixin.ServerLevelAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelStorageSource;

public class CustomServerLevel extends ServerLevel {
  public static final int STOP_AFTER = 1200; // 60 seconds * 20 ticks
  private static final Logger LOGGER = LogUtils.getLogger();
  private boolean isStopped;
  private boolean isClosed;
  private boolean markedForClose;
  private boolean ticketsActivated;
  private boolean spawnSettingsSet;
  private boolean deleteOnClose;

  public CustomServerLevel(
      MinecraftServer minecraftServer,
      Executor executor,
      LevelStorageSource.LevelStorageAccess levelStorageAccess,
      WorldsLevelData serverLevelData,
      ResourceKey<Level> resourceKey,
      LevelStem levelStem,
      List<CustomSpawner> customSpawners,
      @Nullable RandomSequences randomSequences) {
    super(minecraftServer, executor, levelStorageAccess, serverLevelData, resourceKey, levelStem,
        serverLevelData.getWorldData().isDebugWorld(), BiomeManager.obfuscateSeed(serverLevelData.getSeed()),
        customSpawners, true, randomSequences);

    this.isStopped = false;
    this.isClosed = false;
    this.markedForClose = false;
    this.ticketsActivated = false;
    this.spawnSettingsSet = false;
    this.deleteOnClose = false;

    this.getServer().execute(() -> {
      ((MinecraftServerAccessor) this.getServer()).getLevels().put(resourceKey, this);
      ServerWorldEvents.LOAD.invoker().onWorldLoad(this.getServer(), this);

      this.getWorldBorder().setAbsoluteMaxSize(this.getServer().getAbsoluteMaxWorldSize());
      this.getServer().getPlayerList().addWorldborderListener(this);
    });
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    if (this.isStopped) {
      this.kickPlayers(null);

      if (this.markedForClose) {
        return;
      }

      if (this.getChunkSource().chunkMap.hasWork()) {
        this.noSave = false;
        this.getChunkSource().deactivateTicketsOnClosing();
        this.getChunkSource().tick(() -> true, false);
      } else {
        this.markedForClose = true;
      }
      return;
    }
    if (!this.ticketsActivated) {
      TicketStorage ticketStorage = this.getDataStorage().get(TicketStorage.TYPE);
      if (ticketStorage != null) {
        ticketStorage.activateAllDeactivatedTickets();
      }
      this.ticketsActivated = true;
    } else if (!this.spawnSettingsSet) {
      this.setSpawnSettings(((MinecraftServerAccessor) this.getServer()).invokeSpawningMonsters());
      this.spawnSettingsSet = true;
      WorldsManager.LOGGER.info("World prepared: {}", this.dimension().location());
    }
    if (((ServerLevelAccessor) this).getEmptyTime() > STOP_AFTER) {
      this.stop(false);
      return;
    }
    super.tick(booleanSupplier);
  }

  public boolean isDeleteOnClose() {
    return deleteOnClose;
  }

  public boolean isStopped() {
    return isStopped;
  }

  public boolean isClosed() {
    return this.isClosed;
  }

  public boolean isMarkedForClose() {
    return this.markedForClose;
  }

  @Override
  public void close() throws IOException {
    super.close();
    this.isClosed = true;
  }

  @Override
  public long getSeed() {
    return ((WorldsLevelData) this.levelData).getSeed();
  }

  public void stop(boolean deleteOnClose) {
    if (this.isStopped) {
      return;
    }
    this.deleteOnClose = deleteOnClose;
    this.isStopped = true;
  }

  protected void kickPlayers(@Nullable ServerLevel destination) {
    if (this.players().isEmpty())
      return;

    if (destination == null) {
      destination = this.getServer().overworld();
    }

    var players = new ArrayList<>(this.players());

    for (ServerPlayer player : players) {
      player
          .teleport(TeleportTransitionAbstraction.spawnAt(player, destination, TeleportTransition.PLACE_PORTAL_TICKET));
    }
  }
}
