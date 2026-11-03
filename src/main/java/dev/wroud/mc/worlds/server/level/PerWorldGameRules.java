package dev.wroud.mc.worlds.server.level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.network.GameRuleOriginsPayload;
import dev.wroud.mc.worlds.network.GameRuleOriginsPayload.Origin;
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
  public static final SavedDataType<GameRuleMap> TYPE = type(McWorldMod.id("game_rules"));
  public static final TagKey<GameRule<?>> GLOBAL = TagKey.create(Registries.GAME_RULE, McWorldMod.id("global"));
  public static final ScopedValue<GameRuleMap> CHANGED_MAP = ScopedValue.newInstance();

  public static SavedDataType<GameRuleMap> type(Identifier id) {
    return new SavedDataType<>(id, GameRuleMap.TYPE.constructor(), GameRuleMap.TYPE.codec(),
        GameRuleMap.TYPE.dataFixType());
  }

  public static LayeredGameRules create(ServerLevel level) {
    GameRules serverRules = level.getServer().getGameRules();
    Identifier group = WorldGroups.groupOf(level);
    if (group != null) {
      WorldGroups groups = Objects.requireNonNull(WorldGroups.of(level.getServer()));
      return new LayeredGameRules(level, serverRules, groups.rules(group), group);
    }
    GameRuleMap overrides = CustomServerLevel.loadOrConvert(level, TYPE, GameRuleMap.TYPE.id(), vanilla -> {
      Set<GameRule<?>> available = serverRules.availableRules().collect(Collectors.toSet());
      GameRuleMap differing = GameRuleMap.of();
      differing.setFromIf(vanilla,
          rule -> available.contains(rule) && !Objects.equals(vanilla.get(rule), serverRules.get(rule)));
      return differing;
    });
    if (overrides.size() == 0) {
      overrides.setDirty(false);
    }
    return new LayeredGameRules(level, serverRules, overrides, null);
  }

  public static int ownOverrides(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(TYPE).size();
  }

  public static <T> void onWorldGameRuleChanged(LayeredGameRules rules, GameRule<T> rule, T value) {
    MinecraftServer server = rules.level().getServer();
    ScopedValue.where(CHANGED_MAP, rules.overrides()).run(() -> server.onGameRuleChanged(rule, value));
    if (GroupGameRules.isGameModeRule(rule)) {
      GroupGameRules.enforce(rules);
    }
  }

  public static void replayChanges(GameRules previous, LayeredGameRules rules) {
    rules.availableRules().forEach(rule -> replayChange(previous, rules, rule));
  }

  private static <T> void replayChange(GameRules previous, LayeredGameRules rules, GameRule<T> rule) {
    T value = rules.get(rule);
    if (!Objects.equals(previous.get(rule), value)) {
      onWorldGameRuleChanged(rules, rule, value);
    }
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

    Optional<Origin> origin = Optional.empty();
    Map<Identifier, String> serverValues = new HashMap<>();
    if (player.level().getGameRules() instanceof LayeredGameRules rules) {
      origin = Optional.of(new Origin(rules.isGroup(), rules.scopeId()));
      rules.overriddenRules().forEach(rule -> serverValues.put(rule.getIdentifier(), rules.parent().getAsString(rule)));
    }
    ServerPlayNetworking.send(player, new GameRuleOriginsPayload(origin, serverValues));
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
    if (CHANGED_MAP.isBound()) {
      return level.getGameRules() instanceof LayeredGameRules rules && rules.overrides() == CHANGED_MAP.get();
    }
    return !(level.getGameRules() instanceof LayeredGameRules rules && rules.isOverridden(rule));
  }

  private PerWorldGameRules() {
  }
}
