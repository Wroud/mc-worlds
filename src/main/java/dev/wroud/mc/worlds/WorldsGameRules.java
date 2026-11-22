package dev.wroud.mc.worlds;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.jspecify.annotations.Nullable;

public final class WorldsGameRules {
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
  public static final GameRule<Integer> SPAWN_PROTECTION = GameRuleBuilder.forInteger(-1)
      .minValue(-1)
      .category(GameRuleCategory.PLAYER)
      .buildAndRegister(McWorldMod.id("spawn_protection"));

  static void bootstrap() {
  }

  private WorldsGameRules() {
  }
}
