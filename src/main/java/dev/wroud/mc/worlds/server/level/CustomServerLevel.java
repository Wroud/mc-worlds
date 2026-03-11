package dev.wroud.mc.worlds.server.level;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.mixin.ServerLevelAccessor;
import dev.wroud.mc.worlds.server.level.state.ActivationLevelState;
import dev.wroud.mc.worlds.server.level.state.ActiveLevelState;
import dev.wroud.mc.worlds.server.level.state.InitializationLevelState;
import dev.wroud.mc.worlds.server.level.state.LevelState;
import dev.wroud.mc.worlds.server.level.state.StoppedLevelState;
import dev.wroud.mc.worlds.server.level.state.StoppingLevelState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CustomServerLevel extends ServerLevel {
  public static final int STOP_AFTER = 1200; // 60 seconds * 20 ticks
  private boolean isClosed;
  private boolean deleteOnClose;
  private LevelState currentState;
  private final WeatherData weatherData;
  private @Nullable PerWorldClockManager perWorldClockManager;
  private @Nullable EnvironmentAttributeSystem perWorldEnvironmentAttributes;

  public CustomServerLevel(
      MinecraftServer server,
      Executor executor,
      LevelStorageSource.LevelStorageAccess levelStorage,
      WorldsLevelData levelData,
      ResourceKey<Level> dimension,
      LevelStem levelStem,
      List<CustomSpawner> customSpawners) {
    super(server, executor, levelStorage, levelData, dimension, levelStem,
        levelData.getWorldData().isDebugWorld(), BiomeManager.obfuscateSeed(levelData.getSeed()),
        customSpawners, true);

    this.isClosed = false;
    this.deleteOnClose = false;
    this.weatherData = (WeatherData) this.getDataStorage().computeIfAbsent(WeatherData.TYPE);
    ((ServerLevelAccessor) this).invokePrepareWeather(this.weatherData);
    this.perWorldClockManager = this.getDataStorage().computeIfAbsent(PerWorldClockManager.TYPE);
    this.perWorldClockManager.init(this);
    this.perWorldEnvironmentAttributes = EnvironmentAttributeSystem.builder().addDefaultLayers(this).build();
    this.currentState = levelData.isInitialized() ? new ActivationLevelState(this)
        : new InitializationLevelState(this);

    this.getServer().execute(() -> {
      ((MinecraftServerAccessor) this.getServer()).getLevels().put(dimension, this);
      ServerLevelEvents.LOAD.invoker().onLevelLoad(this.getServer(), this);

      this.getWorldBorder().setAbsoluteMaxSize(this.getServer().getAbsoluteMaxWorldSize());
      this.getServer().getPlayerList().addWorldborderListener(this);
    });
  }

  @Override
  public WeatherData getWeatherData() {
    return this.weatherData;
  }

  public @Nullable PerWorldClockManager getPerWorldClockManager() {
    return this.perWorldClockManager;
  }

  @Override
  public @NonNull EnvironmentAttributeSystem environmentAttributes() {
    EnvironmentAttributeSystem pea = this.perWorldEnvironmentAttributes;
    return pea != null ? pea : super.environmentAttributes();
  }

  @Override
	public long getOverworldClockTime() {
		return this.getClockTimeTicks(this.registryAccess().get(WorldClocks.OVERWORLD));
	}

  @Override
	public long getDefaultClockTime() {
		return this.getClockTimeTicks(this.dimensionType().defaultClock());
	}

	private long getClockTimeTicks(final Optional<? extends Holder<WorldClock>> clock) {
		return (Long)clock.map(holder -> {
      var clockManager = this.getPerWorldClockManager();

      if(clockManager != null) {
        return clockManager.getTotalTicks(holder);
      }

      return this.clockManager().getTotalTicks(holder);
    }).orElse(0L);
	}

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    this.currentState.tick(booleanSupplier);
    PerWorldClockManager clock = this.perWorldClockManager;
    if (clock != null && this.tickRateManager().runsNormally() && this.getGameRules().get(GameRules.ADVANCE_TIME)) {
      clock.tick();
    }
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
