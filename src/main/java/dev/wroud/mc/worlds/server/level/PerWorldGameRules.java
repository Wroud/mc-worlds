package dev.wroud.mc.worlds.server.level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.Iterables;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.network.GameRuleOriginsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class PerWorldGameRules {
  public static final SavedDataType<GameRuleMap> TYPE = new SavedDataType<>(
      McWorldMod.id("game_rules"),
      GameRuleMap.TYPE.constructor(),
      GameRuleMap.TYPE.codec(),
      GameRuleMap.TYPE.dataFixType());
  public static final TagKey<GameRule<?>> GLOBAL = TagKey.create(Registries.GAME_RULE, McWorldMod.id("global"));
  public static final ScopedValue<ServerLevel> WORLD_CHANGE = ScopedValue.newInstance();

  public static LayeredGameRules create(ServerLevel level) {
    GameRuleMap overrides = level.getDataStorage().computeIfAbsent(TYPE);
    if (overrides.size() == 0) {
      overrides.setDirty(false);
    }
    return new LayeredGameRules(level, level.getServer().getGameRules(), overrides);
  }

  public static <T> void onWorldGameRuleChanged(ServerLevel level, GameRule<T> rule, T value) {
    ScopedValue.where(WORLD_CHANGE, level).run(() -> level.getServer().onGameRuleChanged(rule, value));
  }

  public static void pruneAll(MinecraftServer server) {
    for (ServerLevel level : server.getAllLevels()) {
      if (level.getGameRules() instanceof LayeredGameRules rules) {
        rules.prune();
      }
    }
  }

  public static void sendOrigins(ServerPlayer player) {
    if (!ServerPlayNetworking.canSend(player, GameRuleOriginsPayload.TYPE)) {
      return;
    }

    Optional<Identifier> world = Optional.empty();
    Map<Identifier, String> serverValues = new HashMap<>();
    if (player.level().getGameRules() instanceof LayeredGameRules rules) {
      world = Optional.of(player.level().dimension().identifier());
      rules.overriddenRules().forEach(rule -> serverValues.put(rule.getIdentifier(), rules.parent().getAsString(rule)));
    }
    ServerPlayNetworking.send(player, new GameRuleOriginsPayload(world, serverValues));
  }

  public static boolean isGlobal(GameRule<?> rule) {
    return BuiltInRegistries.GAME_RULE.wrapAsHolder(rule).is(GLOBAL);
  }

  public static void sendClientRules(ServerPlayer player, GameRules rules) {
    player.connection.send(new ClientboundEntityEventPacket(player,
        rules.get(GameRules.REDUCED_DEBUG_INFO) ? EntityEvent.REDUCED_DEBUG_INFO : EntityEvent.FULL_DEBUG_INFO));
    player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN,
        rules.get(GameRules.IMMEDIATE_RESPAWN) ? 1.0F : 0.0F));
    player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LIMITED_CRAFTING,
        rules.get(GameRules.LIMITED_CRAFTING) ? 1.0F : 0.0F));
  }

  public static List<ServerPlayer> affectedPlayers(List<ServerPlayer> players, GameRule<?> rule) {
    return players.stream().filter(player -> isAffected(player.level(), rule)).toList();
  }

  public static Iterable<ServerLevel> affectedLevels(Iterable<ServerLevel> levels, GameRule<?> rule) {
    return Iterables.filter(levels, level -> isAffected(level, rule));
  }

  private static boolean isAffected(ServerLevel level, GameRule<?> rule) {
    if (WORLD_CHANGE.isBound()) {
      return level == WORLD_CHANGE.get();
    }
    return !(level.getGameRules() instanceof LayeredGameRules rules && rules.isOverridden(rule));
  }

  private PerWorldGameRules() {
  }
}
