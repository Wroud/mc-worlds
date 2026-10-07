package dev.wroud.mc.worlds.client;

import java.util.List;

import com.google.common.collect.ImmutableList;

import dev.wroud.mc.worlds.network.GameRuleOriginsPayload;
import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.InWorldGameRulesScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.gamerules.GameRule;
import org.jspecify.annotations.Nullable;

public final class GameRuleOriginsTooltip {
  static @Nullable GameRuleOriginsPayload origins;

  public static List<FormattedCharSequence> extend(List<FormattedCharSequence> tooltip, GameRule<?> rule) {
    GameRuleOriginsPayload origins = GameRuleOriginsTooltip.origins;
    if (origins == null || origins.world().isEmpty()
        || !(Minecraft.getInstance().gui.screen() instanceof InWorldGameRulesScreen)) {
      return tooltip;
    }

    var id = rule.getIdentifier();
    String serverValue = origins.serverValues().get(id);
    Component line;
    if (PerWorldGameRules.isGlobal(rule)) {
      line = Component.translatable("dev.wroud.mc.worlds.gamerule.origin.global").withStyle(ChatFormatting.GRAY);
    } else if (serverValue != null) {
      line = Component.translatable("dev.wroud.mc.worlds.gamerule.origin.world",
          origins.world().get().toString(), serverValue).withStyle(ChatFormatting.GOLD);
    } else {
      line = Component.translatable("dev.wroud.mc.worlds.gamerule.origin.server").withStyle(ChatFormatting.GRAY);
    }

    return ImmutableList.<FormattedCharSequence>builder().addAll(tooltip).add(line.getVisualOrderText()).build();
  }

  private GameRuleOriginsTooltip() {
  }
}
