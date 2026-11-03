package dev.wroud.mc.worlds.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Collection;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GroupTempCommand {
  private static final String KEY = "dev.wroud.mc.worlds.command.group.temp.";

  public static LiteralArgumentBuilder<CommandSourceStack> build() {
    return literal("temp")
        .then(literal("leave")
            .then(argument("targets", EntityArgument.players())
                .executes(context -> leave(context.getSource(), EntityArgument.getPlayers(context, "targets")))));
  }

  private static int leave(CommandSourceStack source, Collection<ServerPlayer> targets) {
    var worlds = WorldsCommands.worlds(source);
    var sent = (int) targets.stream().filter(worlds::returnHome).count();
    source.sendSuccess(() -> Component.translatable(KEY + "leave.success", sent), true);
    return sent;
  }
}
