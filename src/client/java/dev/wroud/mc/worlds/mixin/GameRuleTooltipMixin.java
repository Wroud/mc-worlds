package dev.wroud.mc.worlds.mixin;

import java.util.List;

import dev.wroud.mc.worlds.client.GameRuleOriginsTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.AbstractGameRulesScreen$RuleList$1")
public class GameRuleTooltipMixin {

    @ModifyArg(
        method = "addEntry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/AbstractGameRulesScreen$EntryFactory;create(Lnet/minecraft/network/chat/Component;Ljava/util/List;Ljava/lang/String;Lnet/minecraft/world/level/gamerules/GameRule;)Lnet/minecraft/client/gui/screens/worldselection/AbstractGameRulesScreen$RuleEntry;"),
        index = 1)
    private List<FormattedCharSequence> mcworlds$origin(Component name, List<FormattedCharSequence> tooltip,
            String narration, GameRule<?> rule) {
        return GameRuleOriginsTooltip.extend(tooltip, rule);
    }
}
