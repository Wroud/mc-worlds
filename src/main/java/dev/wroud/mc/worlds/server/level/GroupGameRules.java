package dev.wroud.mc.worlds.server.level;

import com.mojang.serialization.Codec;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public final class GroupGameRules {
  public enum DefaultGameMode implements StringRepresentable {
    server(null),
    survival(GameType.SURVIVAL),
    creative(GameType.CREATIVE),
    adventure(GameType.ADVENTURE),
    spectator(GameType.SPECTATOR);

    public static final Codec<DefaultGameMode> CODEC = StringRepresentable.fromEnum(DefaultGameMode::values);

    private final @Nullable GameType gameType;

    DefaultGameMode(@Nullable GameType gameType) {
      this.gameType = gameType;
    }

    public GameType resolve(MinecraftServer server) {
      return this.gameType != null ? this.gameType : server.getDefaultGameType();
    }

    @Override
    public String getSerializedName() {
      return this.name();
    }
  }

  public static final GameRule<DefaultGameMode> DEFAULT_GAME_MODE = GameRuleBuilder.forEnum(DefaultGameMode.server)
      .category(GameRuleCategory.PLAYER)
      .codec(DefaultGameMode.CODEC)
      .buildAndRegister(McWorldMod.id("default_game_mode"));
  public static final GameRule<Boolean> FORCE_GAME_MODE = GameRuleBuilder.forBoolean(false)
      .category(GameRuleCategory.PLAYER)
      .buildAndRegister(McWorldMod.id("force_game_mode"));

  public static void bootstrap() {
  }

  public static boolean isGameModeRule(GameRule<?> rule) {
    return rule == DEFAULT_GAME_MODE || rule == FORCE_GAME_MODE;
  }

  public static boolean isRefused(GameRule<?> rule, Level level) {
    return isGameModeRule(rule) && WorldGroups.groupOf(level) == null;
  }

  public static @Nullable GameType newPlayerMode(ServerPlayer player, @Nullable GameType loaded) {
    ServerLevel level = player.level();
    if (loaded != null || WorldGroups.groupOf(level) == null) {
      return null;
    }
    DefaultGameMode mode = level.getGameRules().get(DEFAULT_GAME_MODE);
    return mode != DefaultGameMode.server ? mode.resolve(level.getServer()) : null;
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
    for (ServerPlayer player : rules.level().getServer().getPlayerList().getPlayers()) {
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
    if (!rules.get(FORCE_GAME_MODE) || isExempt(server, player)) {
      return null;
    }
    return rules.get(DEFAULT_GAME_MODE).resolve(server);
  }

  private static boolean isExempt(MinecraftServer server, ServerPlayer player) {
    return !(server instanceof DedicatedServer) && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
  }

  private GroupGameRules() {
  }
}
