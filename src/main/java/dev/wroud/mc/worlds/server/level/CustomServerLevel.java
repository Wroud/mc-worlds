package dev.wroud.mc.worlds.server.level;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.server.level.state.ActivationLevelState;
import dev.wroud.mc.worlds.server.level.state.ActiveLevelState;
import dev.wroud.mc.worlds.server.level.state.InitializationLevelState;
import dev.wroud.mc.worlds.server.level.state.LevelState;
import dev.wroud.mc.worlds.server.level.state.StoppedLevelState;
import dev.wroud.mc.worlds.server.level.state.StoppingLevelState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;

public class CustomServerLevel extends ServerLevel {
  public static final int STOP_AFTER = 1200; // 60 seconds * 20 ticks
  private boolean isClosed;
  private boolean deleteOnClose;
  private LevelState currentState;

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

    this.isClosed = false;
    this.deleteOnClose = false;
    this.currentState = serverLevelData.isInitialized() ? new ActivationLevelState(this)
        : new InitializationLevelState(this);

    this.getServer().execute(() -> {
      ((MinecraftServerAccessor) this.getServer()).getLevels().put(resourceKey, this);
      ServerWorldEvents.LOAD.invoker().onWorldLoad(this.getServer(), this);

      this.getWorldBorder().setAbsoluteMaxSize(this.getServer().getAbsoluteMaxWorldSize());
      this.getServer().getPlayerList().addWorldborderListener(this);
    });
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    this.currentState.tick(booleanSupplier);
    super.tick(booleanSupplier);
  }

  public boolean canTeleport() {
    return !this.isStopping() && !this.isStopped();
  }

  public boolean isActive() {
    return this.currentState instanceof ActiveLevelState;
  }

  public boolean isDeleteOnClose() {
    return deleteOnClose;
  }

  public boolean isStopping() {
    return this.currentState instanceof StoppingLevelState;
  }

  public boolean isClosed() {
    return this.isClosed;
  }

  public boolean isStopped() {
    return this.currentState instanceof StoppedLevelState;
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
    if (this.isStopped() || this.isStopping()) {
      return;
    }
    this.deleteOnClose = deleteOnClose;
    this.setState(StoppingLevelState::new);
  }

  public void setState(LevelStateFactory<? extends LevelState> factory) {
    this.currentState = factory.create(this);
  }

  public interface LevelStateFactory<T extends LevelState> {
    T create(CustomServerLevel level);
  }
}
