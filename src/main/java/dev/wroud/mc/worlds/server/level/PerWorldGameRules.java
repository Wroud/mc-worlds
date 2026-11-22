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
import dev.wroud.mc.worlds.WorldsGameRules;
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
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

public final class PerWorldGameRules {
  public static final SavedDataType<GameRuleMap> TYPE = type(McWorldMod.id("game_rules"));
  public static final TagKey<GameRule<?>> GLOBAL = TagKey.create(Registries.GAME_RULE, McWorldMod.id("global"));
  public static final ScopedValue<GameRuleMap> CHANGED_MAP = ScopedValue.newInstance();

  public static SavedDataType<GameRuleMap> type(Identifier id) {
    return new SavedDataType<>(id, GameRuleMap.TYPE.constructor(), GameRuleMap.TYPE.codec(),
        GameRuleMap.TYPE.dataFixType());
  }

  public static LayeredGameRules create(ServerLevel level) {
    Identifier group = WorldGroups.groupOf(level);
    if (group != null) {
      return Objects.requireNonNull(WorldGroups.of(level.getServer())).gameRules(group);
    }
    GameRules serverRules = level.getServer().getGameRules();
    GameRuleMap overrides = CustomServerLevel.loadOrConvert(level, TYPE, GameRuleMap.TYPE.id(), vanilla -> {
      Set<GameRule<?>> available = serverRules.availableRules().collect(Collectors.toSet());
      GameRuleMap differing = GameRuleMap.of();
      differing.setFromIf(vanilla,
          rule -> available.contains(rule) && !Objects.equals(vanilla.get(rule), serverRules.get(rule)));
      return differing;
    });
    return new LayeredGameRules(level.getServer(), overrides, level.dimension().identifier(), false);
  }

  public static int ownOverrides(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(TYPE).size();
  }

  public static <T> void onWorldGameRuleChanged(LayeredGameRules rules, GameRule<T> rule, T value) {
    MinecraftServer server = rules.server();
    ScopedValue.where(CHANGED_MAP, rules.overrides()).run(() -> server.onGameRuleChanged(rule, value));
    if (isGameModeRule(rule)) {
      enforce(rules);
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

  public static boolean isGameModeRule(GameRule<?> rule) {
    return rule == WorldsGameRules.DEFAULT_GAME_MODE || rule == WorldsGameRules.FORCE_GAME_MODE;
  }

  public static boolean isRefused(GameRule<?> rule, ServerLevel level) {
    return level.getGameRules() instanceof LayeredGameRules rules ? rules.refuses(rule) : isGameModeRule(rule);
  }

  public static @Nullable GameType newPlayerMode(ServerPlayer player, @Nullable GameType loaded) {
    ServerLevel level = player.level();
    if (loaded != null || WorldGroups.groupOf(level) == null) {
      return null;
    }
    WorldsGameRules.DefaultGameMode mode = level.getGameRules().get(WorldsGameRules.DEFAULT_GAME_MODE);
    return mode != WorldsGameRules.DefaultGameMode.server ? mode.resolve(level.getServer()) : null;
  }

  public static int spawnProtection(ServerLevel level, int serverRadius) {
    int radius = level.getGameRules().get(WorldsGameRules.SPAWN_PROTECTION);
    return radius < 0 ? serverRadius : radius;
  }

  public static void applyOnEnter(ServerPlayer player, ServerLevel level) {
    GameType mode = forced(level, player);
    if (mode != null) {
      player.setGameMode(mode);
    }
  }

  public static void enforce(LayeredGameRules rules) {
    if (!rules.isGroup()) {
      return;
    }
    for (ServerPlayer player : rules.server().getPlayerList().getPlayers()) {
      if (player.level().getGameRules() instanceof LayeredGameRules layered && layered.overrides() == rules.overrides()) {
        applyOnEnter(player, player.level());
      }
    }
  }

  private static @Nullable GameType forced(ServerLevel level, ServerPlayer player) {
    if (WorldGroups.groupOf(level) == null) {
      return null;
    }
    MinecraftServer server = level.getServer();
    GameRules rules = level.getGameRules();
    if (!rules.get(WorldsGameRules.FORCE_GAME_MODE) || isExempt(server, player)) {
      return null;
    }
    return rules.get(WorldsGameRules.DEFAULT_GAME_MODE).resolve(server);
  }

  private static boolean isExempt(MinecraftServer server, ServerPlayer player) {
    return !(server instanceof DedicatedServer) && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
  }

  private PerWorldGameRules() {
  }
}
