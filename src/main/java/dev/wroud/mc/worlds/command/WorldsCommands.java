package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.wroud.mc.worlds.McWorldInitializer;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

import static net.minecraft.commands.Commands.literal;


public class WorldsCommands {

	public static final SuggestionProvider<CommandSourceStack> WORLD_SUGGESTIONS = (context,
			builder) -> SharedSuggestionProvider.suggestResource(
					context.getSource().getServer().levelKeys().stream().map(ResourceKey::location), builder);

	public static final SuggestionProvider<CommandSourceStack> CUSTOM_WORLD_SUGGESTIONS = (context,
			builder) -> SharedSuggestionProvider.suggestResource(
					McWorldInitializer.getMcWorld(context.getSource().getServer()).getManadger().getWorldIds(), builder);

	public static final SimpleCommandExceptionType UNKNOWN_WORLD_EXCEPTION = new SimpleCommandExceptionType(
			Component.translatable("dev.wroud.mc.worlds.command.exception.unknown_world"));

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext commandBuildContext) {
		var root = dispatcher.register(
				literal("worlds")
						.requires(Permissions.require("dev.wroud.mc.worlds.commands", 2))
						.then(DeleteCommand.build())
						.then(TeleportCommand.build())
						.then(CreateCommand.build()));

		dispatcher.register(
				literal("worlds")
						.requires(Permissions.require("dev.wroud.mc.worlds.commands", 2))
						.redirect(root));
	}
}
