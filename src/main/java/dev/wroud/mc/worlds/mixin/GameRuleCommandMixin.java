package dev.wroud.mc.worlds.mixin;

import java.util.function.Supplier;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;

import dev.wroud.mc.worlds.command.WorldGameRuleCommand;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRuleCommand.class)
public class GameRuleCommandMixin {

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
