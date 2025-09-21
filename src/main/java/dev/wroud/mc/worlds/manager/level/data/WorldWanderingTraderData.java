package dev.wroud.mc.worlds.manager.level.data;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

public class WorldWanderingTraderData {
  public static final Codec<WorldWanderingTraderData> CODEC = RecordCodecBuilder
      .create(instance -> instance.group(
          Codec.INT.optionalFieldOf("wandering_trader_spawn_delay", 0)
              .forGetter(wed -> wed.wanderingTraderSpawnDelay),
          Codec.INT.optionalFieldOf("wandering_trader_spawn_chance", 0)
              .forGetter(wed -> wed.wanderingTraderSpawnChance),
          UUIDUtil.CODEC.optionalFieldOf("wandering_trader_id").forGetter(wed -> wed.wanderingTraderId))
          .apply(instance, WorldWanderingTraderData::new));

  public int wanderingTraderSpawnDelay;
  public int wanderingTraderSpawnChance;
  public Optional<UUID> wanderingTraderId;

  public WorldWanderingTraderData(int wanderingTraderSpawnDelay, int wanderingTraderSpawnChance,
      Optional<UUID> wanderingTraderId) {
    this.wanderingTraderSpawnDelay = wanderingTraderSpawnDelay;
    this.wanderingTraderSpawnChance = wanderingTraderSpawnChance;
    this.wanderingTraderId = wanderingTraderId;
  }

  public WorldWanderingTraderData() {
    this.wanderingTraderSpawnDelay = 0;
    this.wanderingTraderSpawnChance = 0;
    this.wanderingTraderId = Optional.empty();
  }
}
