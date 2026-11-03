package dev.wroud.mc.worlds.mixin;

import java.util.function.Supplier;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.wroud.mc.worlds.command.WorldGameRuleCommand;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRuleCommand.class)
public class GameRuleCommandMixin {

    @Inject(method = "setRule", at = @At("HEAD"))
    private static void mcworlds$refuseGroupOnlyRules(CommandContext<CommandSourceStack> context, GameRule<?> rule,
            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        WorldGameRuleCommand.requireGroup(context.getSource(), rule);
    }

    @WrapOperation(
        method = "setRule",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/CommandSourceStack;sendSuccess(Ljava/util/function/Supplier;Z)V"))
    private static void mcworlds$scopedSet(CommandSourceStack source, Supplier<Component> message, boolean broadcast,
            Operation<Void> original, CommandContext<CommandSourceStack> context, GameRule<?> rule) {
        if (source.getLevel() instanceof CustomServerLevel level) {
            Component scoped = WorldGameRuleCommand.scoped("set", level, rule);
            message = () -> scoped;
        }
        original.call(source, message, broadcast);
    }

    @WrapOperation(
        method = "queryRule",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/CommandSourceStack;sendSuccess(Ljava/util/function/Supplier;Z)V"))
    private static void mcworlds$scopedQuery(CommandSourceStack source, Supplier<Component> message, boolean broadcast,
            Operation<Void> original, CommandSourceStack querySource, GameRule<?> rule) {
        if (source.getLevel() instanceof CustomServerLevel level) {
            Component scoped = WorldGameRuleCommand.scoped("query", level, rule);
            message = () -> scoped;
        }
        original.call(source, message, broadcast);
    }
}
