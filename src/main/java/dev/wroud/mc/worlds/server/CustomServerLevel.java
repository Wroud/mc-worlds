package dev.wroud.mc.worlds.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.wroud.mc.worlds.manadger.LevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener.DelegateBorderChangeListener;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;

public class CustomServerLevel extends ServerLevel {
  private static final Logger LOGGER = LogUtils.getLogger();
  private boolean isManuallyStopped;
  private boolean isSaving;
  private boolean isClosed;
  private DelegateBorderChangeListener borderChangeListener;

  public CustomServerLevel(
      MinecraftServer minecraftServer,
      Executor executor,
      LevelStorageSource.LevelStorageAccess levelStorageAccess,
      LevelData serverLevelData,
      ResourceKey<Level> resourceKey,
      LevelStem levelStem,
      ChunkProgressListener chunkProgressListener,
      boolean bl,
      List<CustomSpawner> list,
      @Nullable RandomSequences randomSequences) {
    super(minecraftServer, executor, levelStorageAccess, serverLevelData, resourceKey, levelStem, chunkProgressListener,
        bl, BiomeManager.obfuscateSeed(serverLevelData.getSeed()), list, true, randomSequences);

    this.isManuallyStopped = false;
    this.isSaving = false;
    this.isClosed = false;
    this.borderChangeListener = new DelegateBorderChangeListener(this.getWorldBorder());

    this.getServer().execute(() -> {
      ((MinecraftServerAccessor) this.getServer()).getLevels().put(resourceKey, this);
      ServerWorldEvents.LOAD.invoker().onWorldLoad(this.getServer(), this);

      this.getServer().overworld().getWorldBorder().addListener(this.borderChangeListener);
    });
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    if (this.isManuallyStopped) {
      this.kickPlayers(null);

      if (this.isSaving) {
        return;
      }

      if (this.getChunkSource().chunkMap.hasWork()) {
        this.getChunkSource().deactivateTicketsOnClosing();
      } else {
        this.isSaving = true;
        this.getServer().execute(() -> {
          ((MinecraftServerAccessor) this.getServer()).getLevels().remove(this.dimension());
          LOGGER.info("Saving chunks for level '{}'/{}", this, this.dimension().location());
          this.save(null, true, this.noSave);

          try {
            this.close();
            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(this.getServer(), this);
          } catch (IOException var5) {
            LOGGER.error("Exception closing the level", (Throwable) var5);
          }
        });
        return;
      }
    }
    super.tick(booleanSupplier);
  }

  public boolean isManuallyStopped() {
    return isManuallyStopped && isClosed;
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (this.isManuallyStopped) {
      this.getServer().overworld().getWorldBorder()
          .removeListener(this.borderChangeListener);
    }
    this.isClosed = true;
  }

  @Override
  public long getSeed() {
    this.getServer().overworld();
    return ((LevelData) this.levelData).getSeed();
  }

  public void stop(boolean noSave) {
    if (this.isManuallyStopped) {
      return;
    }
    this.noSave = noSave;
    this.isManuallyStopped = true;
  }

  protected void kickPlayers(@Nullable ServerLevel destination) {
    if (this.players().isEmpty())
      return;

    if (destination == null) {
      destination = this.getServer().overworld();
    }
    destination.noSave();

    var players = new ArrayList<>(this.players());

    var target = new TeleportTransition(destination, destination.getSharedSpawnPos().getBottomCenter(), Vec3.ZERO,
        destination.getSharedSpawnAngle(), 0.0F,
        Relative.union(Relative.DELTA, Relative.ROTATION), TeleportTransition.DO_NOTHING);

    for (ServerPlayer player : players) {
      player.teleport(target);
    }
  }
}
