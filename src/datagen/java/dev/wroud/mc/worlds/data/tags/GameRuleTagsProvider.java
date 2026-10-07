package dev.wroud.mc.worlds.data.tags;

import java.util.concurrent.CompletableFuture;

import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class GameRuleTagsProvider extends FabricTagsProvider<GameRule<?>> {
  public GameRuleTagsProvider(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> completableFuture) {
    super(packOutput, Registries.GAME_RULE, completableFuture);
  }

  @Override
  protected void addTags(HolderLookup.Provider provider) {
    this.builder(PerWorldGameRules.GLOBAL)
        .add(key(GameRules.SEND_COMMAND_FEEDBACK))
        .add(key(GameRules.LOG_ADMIN_COMMANDS))
        .add(key(GameRules.MAX_COMMAND_SEQUENCE_LENGTH))
        .add(key(GameRules.MAX_COMMAND_FORKS))
        .add(key(GameRules.MAX_BLOCK_MODIFICATIONS));
  }

  private static ResourceKey<GameRule<?>> key(GameRule<?> rule) {
    return BuiltInRegistries.GAME_RULE.getResourceKey(rule).orElseThrow();
  }
}
