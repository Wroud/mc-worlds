package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

import static dev.wroud.mc.worlds.command.WorldsCommands.WORLD_SUGGESTIONS;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class TeleportCommand {
	public static String command(Identifier id) {
		return "/worlds tp " + id;
	}

	public static Component link(Identifier id) {
		return Component.translatable("dev.wroud.mc.worlds.command.create.success.teleport")
				.withStyle(style -> style
						.withColor(ChatFormatting.AQUA)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand(command(id))));
	}

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return literal("tp")
				.then(
						argument("id", IdentifierArgument.id())
								.suggests(WORLD_SUGGESTIONS)
								.executes(context -> teleport(context.getSource(),
										IdentifierArgument.getId(context, "id"),
										List.of(context.getSource().getPlayerOrException())))
								.then(
										argument("targets", EntityArgument.players())
												.executes(context -> teleport(context.getSource(),
														IdentifierArgument.getId(context, "id"),
														EntityArgument.getPlayers(context, "targets")))));
	}

	public static int teleport(CommandSourceStack source, Identifier id, Collection<ServerPlayer> targets)
			throws CommandSyntaxException {
		var worlds = WorldsCommands.worlds(source);
		WorldsCommands.call(() -> targets.forEach(player -> worlds.teleport(player, id)));
		source.sendSuccess(() -> Component.translatable("dev.wroud.mc.worlds.command.teleport.success", id.toString()),
				false);
		return 1;
	}

}
