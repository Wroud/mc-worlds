package dev.wroud.mc.worlds.server.level;

import java.util.Map;
import java.util.function.Consumer;

import dev.wroud.mc.worlds.McWorldMod;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.ClockState;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.PackedClockStates;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.timeline.Timeline;

import org.apache.commons.lang3.mutable.MutableBoolean;

public class PerWorldClockManager extends SavedData implements ClockManager {
  public static final SavedDataType<PerWorldClockManager> TYPE = new SavedDataType<>(
      McWorldMod.id("world_clocks"),
      () -> new PerWorldClockManager(PackedClockStates.EMPTY),
      PackedClockStates.CODEC.xmap(PerWorldClockManager::new, PerWorldClockManager::packState),
      DataFixTypes.SAVED_DATA_WORLD_CLOCKS);

  private ServerLevel serverLevel;
  private final PackedClockStates packedInitialStates;
  private final Map<Holder<WorldClock>, ClockInstance> clocks = new Reference2ObjectOpenHashMap<>();

  private PerWorldClockManager(PackedClockStates packedInitialStates) {
    this.packedInitialStates = packedInitialStates;
  }

  public void init(ServerLevel serverLevel) {
    this.serverLevel = serverLevel;
    serverLevel.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
        .listElements()
        .forEach(def -> this.clocks.put(def, new ClockInstance()));

    serverLevel.registryAccess().lookupOrThrow(Registries.TIMELINE)
        .listElements()
        .forEach(timeline -> ((Timeline) timeline.value()).registerTimeMarkers(this::registerTimeMarker));

		this.packedInitialStates.clocks().forEach((definition, state) -> {
			ClockInstance instance = this.getInstance(definition);
			instance.loadFrom(state);
		});
  }

  private void registerTimeMarker(ResourceKey<ClockTimeMarker> id, ClockTimeMarker marker) {
		this.getInstance(marker.clock()).timeMarkers.put(id, marker);
  }

	public PackedClockStates packState() {
		return new PackedClockStates(Util.mapValues(this.clocks, ClockInstance::packState));
	}

	public void tick() {
		boolean advanceTime = this.serverLevel.getGameRules().get(GameRules.ADVANCE_TIME);
		if (advanceTime) {
			this.clocks.values().forEach(ClockInstance::tick);
			this.setDirty();
		}
	}

  private ClockInstance getInstance(final Holder<WorldClock> definition) {
    ClockInstance instance = this.clocks.get(definition);
    if (instance == null) {
      throw new IllegalStateException("No clock initialized for definition: " + definition);
    } else {
      return instance;
    }
  }

  public void setTotalTicks(Holder<WorldClock> clock, long totalTicks) {
		this.modifyClock(clock, instance -> {
			instance.totalTicks = totalTicks;
			instance.partialTick = 0.0F;
		});
  }

  public boolean moveToTimeMarker(Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> timeMarkerId) {
		MutableBoolean set = new MutableBoolean();
		this.modifyClock(clock, instance -> {
			ClockTimeMarker timeMarker = (ClockTimeMarker)instance.timeMarkers.get(timeMarkerId);
			if (timeMarker != null) {
				instance.totalTicks = timeMarker.resolveTimeToMoveTo(instance.totalTicks);
				instance.partialTick = 0.0F;
				set.setTrue();
			}
		});
		return set.booleanValue();
  }

  public void addTicks(Holder<WorldClock> clock, int ticks) {
		this.modifyClock(clock, instance -> instance.totalTicks = Math.max(instance.totalTicks + ticks, 0L));
  }

  public void setPaused(Holder<WorldClock> clock, boolean paused) {
		this.modifyClock(clock, instance -> instance.paused = paused);
  }

  public void setRate(Holder<WorldClock> clock, float rate) {
    this.modifyClock(clock, instance -> instance.rate = rate);
  }

  private void modifyClock(final Holder<WorldClock> clock, final Consumer<? super ClockInstance> action) {
    ClockInstance instance = this.getInstance(clock);
    action.accept(instance);
    Map<Holder<WorldClock>, ClockNetworkState> updates = Map.of(clock, instance.packNetworkState(this.serverLevel));
    this.serverLevel.getServer().getPlayerList().broadcastAll(new ClientboundSetTimePacket(this.getGameTime(), updates),
        this.serverLevel.dimension());
    this.serverLevel.environmentAttributes().invalidateTickCache();
    this.setDirty();
  }

	@Override
	public long getTotalTicks(final Holder<WorldClock> definition) {
		return this.getInstance(definition).totalTicks;
	}

  public ClientboundSetTimePacket createFullSyncPacket() {
    return new ClientboundSetTimePacket(this.getGameTime(),
        Util.mapValues(this.clocks, clock -> clock.packNetworkState(this.serverLevel)));
  }

  private long getGameTime() {
    return this.serverLevel.getGameTime();
  }

  private static class ClockInstance {
    private final Map<ResourceKey<ClockTimeMarker>, ClockTimeMarker> timeMarkers = new Reference2ObjectOpenHashMap<>();
    private long totalTicks;
    private float partialTick;
    private float rate = 1.0F;
    private boolean paused;

    public void loadFrom(ClockState state) {
      this.totalTicks = state.totalTicks();
      this.partialTick = state.partialTick();
      this.rate = state.rate();
      this.paused = state.paused();
    }

    public void tick() {
      if (!this.paused) {
        this.partialTick += this.rate;
        int fullTicks = Mth.floor(this.partialTick);
        this.partialTick -= fullTicks;
        this.totalTicks += fullTicks;
      }
    }

    public ClockState packState() {
      return new ClockState(this.totalTicks, this.partialTick, this.rate, this.paused);
    }

    public ClockNetworkState packNetworkState(final ServerLevel serverLevel) {
      boolean advanceTime = serverLevel.getGameRules().get(GameRules.ADVANCE_TIME);
      boolean paused = this.paused || !advanceTime;
      return new ClockNetworkState(this.totalTicks, this.partialTick, paused ? 0.0F : this.rate);
    }
  }
}
