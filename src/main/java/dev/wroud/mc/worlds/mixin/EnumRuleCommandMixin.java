package dev.wroud.mc.worlds.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.wroud.mc.worlds.command.WorldGameRuleCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.gamerule.EnumRuleCommand", remap = false)
public class EnumRuleCommandMixin {

    @Inject(method = "executeAndSetEnum", at = @At("HEAD"))
    private static <E extends Enum<E>> void mcworlds$refuseGroupOnlyRules(CommandContext<CommandSourceStack> context,
            E value, GameRule<E> rule, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        WorldGameRuleCommand.requireGroup(context.getSource(), rule);
    }
}
