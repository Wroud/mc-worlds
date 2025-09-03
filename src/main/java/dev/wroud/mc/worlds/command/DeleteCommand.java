package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.wroud.mc.worlds.McWorldInitializer;
import me.drex.message.api.LocalizedMessage;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;

import static dev.wroud.mc.worlds.command.WorldsCommands.CUSTOM_WORLD_SUGGESTIONS;
import static dev.wroud.mc.worlds.command.WorldsCommands.UNKNOWN_WORLD_EXCEPTION;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DeleteCommand {
	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return literal("delete")
				.requires(Permissions.require("dev.wroud.mc.worlds.command.delete", 2))
				.then(
						argument("id", ResourceLocationArgument.id())
								.suggests(CUSTOM_WORLD_SUGGESTIONS)
								.executes(context -> delete(context.getSource(),
										ResourceLocationArgument.getId(context, "id"))));
	}

	public static int delete(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
		boolean success = McWorldInitializer.getMcWorld().remove(id);

		if (!success) {
			throw UNKNOWN_WORLD_EXCEPTION.create();
		}
		source.sendSuccess(() -> LocalizedMessage.builder("dev.wroud.mc.worlds.command.delete.success")
				.addPlaceholder("id", id.toString()).build(), false);
		return 1;
	}
}
