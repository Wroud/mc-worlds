package dev.wroud.mc.worlds.server.level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.WorldsCreator;
import dev.wroud.mc.worlds.manager.WorldsException;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import dev.wroud.mc.worlds.mixin.ServerLevelAccessor;
import dev.wroud.mc.worlds.server.level.state.ActivationLevelState;
import dev.wroud.mc.worlds.server.level.state.ActiveLevelState;
import dev.wroud.mc.worlds.server.level.state.InitializationLevelState;
import dev.wroud.mc.worlds.server.level.state.LevelState;
import dev.wroud.mc.worlds.server.level.state.SpawnPreparationHelper;
import dev.wroud.mc.worlds.server.level.state.StoppedLevelState;
import dev.wroud.mc.worlds.server.level.state.StoppingLevelState;
import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CustomServerLevel extends ServerLevel {
  public static final int STOP_AFTER = 1200; // 60 seconds * 20 ticks
  private boolean isClosed;
  private boolean deleteOnClose;
  private LevelState currentState;
  private final WeatherData weatherData;
  private final @Nullable WorldGroups groups;
  private @Nullable Identifier groupId;
  private @Nullable RespawnData groupSpawn;
  private final CompletableFuture<ServerLevel> ready = new CompletableFuture<>();
  private @Nullable LayeredGameRules perWorldGameRules;
  private final @Nullable ServerClockManager perWorldClockManager;
  private final @Nullable EnvironmentAttributeSystem perWorldEnvironmentAttributes;

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
    this.groups = WorldGroups.of(server);
    this.groupId = this.resolveGroup();
    this.perWorldGameRules = PerWorldGameRules.create(this);
    this.perWorldClockManager = PerWorldClocks.create(this);
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

  @Override
  public GameRules getGameRules() {
    LayeredGameRules rules = this.perWorldGameRules;
    return rules != null ? rules : super.getGameRules();
  }

  public @Nullable Identifier getGroupId() {
    return this.groupId;
  }

  private @Nullable Identifier resolveGroup() {
    return this.groups != null ? this.groups.groupOf(this.dimension().identifier()) : null;
  }

  public void refreshGroup() {
    LayeredGameRules previous = this.perWorldGameRules;
    this.groupId = this.resolveGroup();
    LayeredGameRules rules = PerWorldGameRules.create(this);
    this.perWorldGameRules = rules;
    for (ServerPlayer player : this.players()) {
      PerWorldGameRules.sendOrigins(player);
      GroupGameRules.applyOnEnter(player, this);
    }
    if (previous != null) {
      PerWorldGameRules.replayChanges(previous, rules);
    }
    this.refreshGroupSpawn();
  }

  public void refreshGroupSpawn() {
    this.groupSpawn = null;
    var packet = new ClientboundSetDefaultSpawnPositionPacket(this.getRespawnData());
    for (ServerPlayer player : this.players()) {
      player.connection.send(packet);
    }
  }

  @Override
  public RespawnData getRespawnData() {
    Identifier group = this.groupId;
    WorldGroups groups = this.groups;
    if (group == null || groups == null) {
      return super.getRespawnData();
    }
    RespawnData spawn = this.groupSpawn;
    if (spawn == null) {
      spawn = groups.spawnOf(group).spawn();
      this.groupSpawn = spawn;
    }
    return spawn;
  }

  @Override
  public boolean isAllowedToEnterPortal(Level toLevel) {
    if (toLevel instanceof CustomServerLevel destination && !destination.canTeleport()) {
      return false;
    }
    return !DimensionDetectionUtil.isNetherLikeDimension(toLevel)
        || this.getGameRules().get(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS);
  }

  @Override
  public ServerClockManager clockManager() {
    ServerClockManager clock = this.perWorldClockManager;
    return clock != null ? clock : super.clockManager();
  }

  @Override
  public @NonNull EnvironmentAttributeSystem environmentAttributes() {
    EnvironmentAttributeSystem pea = this.perWorldEnvironmentAttributes;
    return pea != null ? pea : super.environmentAttributes();
  }

  @Override
  public void tick(BooleanSupplier booleanSupplier) {
    this.currentState.tick(booleanSupplier);
    ServerClockManager clock = this.perWorldClockManager;
    if (clock != null && this.tickRateManager().runsNormally()) {
      clock.tick();
    }
    super.tick(booleanSupplier);
  }

  @Override
  protected void tickTime() {
    WorldsLevelData worldsLevelData = (WorldsLevelData) this.levelData;
    worldsLevelData.setGameTime(worldsLevelData.getGameTime() + 1L);
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

  public boolean isRespawnLevel() {
    return this.dimension().equals(this.getServer().getRespawnData().dimension());
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
    if (deleteOnClose && !this.deleteOnClose) {
      this.deleteOnClose = true;
      McWorldMod.getManager(this.getServer()).ifPresent(
          manager -> manager.getTemporaryStorage().forget(this.dimension().identifier()));
      if (this.isRespawnLevel()) {
        this.getServer().setRespawnData(SpawnPreparationHelper.findInitialSpawn(this.getServer().overworld()));
      }
    }

    if (!this.isStopped() && !this.isStopping()) {
      this.setState(StoppingLevelState::new);
    }
  }

  public void setState(LevelStateFactory<? extends LevelState> factory) {
    this.currentState = factory.create(this);
    if (this.isActive()) {
      this.getServer().execute(this::markReady);
    }
  }

  private void markReady() {
    if (!this.canTeleport()) {
      return;
    }
    McWorldMod.getManager(this.getServer()).ifPresent(
        manager -> manager.getTemporaryStorage().worldReady(this.dimension().identifier()));
    this.ready.complete(this);
  }

  public CompletableFuture<ServerLevel> whenReady() {
    if (!this.canTeleport()) {
      return CompletableFuture.failedFuture(this.notLoaded());
    }
    var result = new CompletableFuture<ServerLevel>();
    this.ready.whenComplete((level, error) -> {
      if (error != null) {
        result.completeExceptionally(error);
      } else {
        result.complete(level);
      }
    });
    return result;
  }

  public void failReady() {
    this.ready.completeExceptionally(this.notLoaded());
  }

  private WorldsException notLoaded() {
    return new WorldsException(WorldsCreator.NOT_LOADED, this.dimension().identifier());
  }

  static <T extends SavedData> T loadOrConvert(ServerLevel level, SavedDataType<T> type, Identifier vanillaId,
      UnaryOperator<T> convert) {
    SavedDataStorage storage = level.getDataStorage();
    T data = storage.get(type);
    if (data != null) {
      return data;
    }
    Path file = vanillaId.withSuffix(".dat").resolveAgainst(((MinecraftServerAccessor) level.getServer())
        .getStorageSource().getDimensionPath(level.dimension()).resolve(LevelResource.DATA.id()));
    if (!Files.exists(file)) {
      return storage.computeIfAbsent(type);
    }
    try {
      CompoundTag tag = storage.readTagFromDisk(file, type.dataFixType(),
          SharedConstants.getCurrentVersion().dataVersion().version());
      T converted = convert.apply(type.codec()
          .parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get("data")).getOrThrow());
      storage.set(type, converted);
      storage.scheduleSave().thenRun(() -> {
        try {
          Files.deleteIfExists(file);
        } catch (IOException e) {
          McWorldMod.LOGGER.warn("Could not delete {}", file, e);
        }
      });
      return converted;
    } catch (IOException | RuntimeException e) {
      McWorldMod.LOGGER.error("Could not convert {}", file, e);
      return storage.computeIfAbsent(type);
    }
  }

  public interface LevelStateFactory<T extends LevelState> {
    T create(CustomServerLevel level);
  }
}
