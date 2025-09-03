package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;

import static dev.wroud.mc.worlds.command.WorldsCommands.UNKNOWN_WORLD_EXCEPTION;
import static dev.wroud.mc.worlds.command.WorldsCommands.WORLD_SUGGESTIONS;
import static me.drex.message.api.LocalizedMessage.builder;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class TeleportCommand {
	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return literal("tp")
				.requires(Permissions.require("dev.wroud.mc.worlds.command.teleport", 2))
				.then(
						argument("id", ResourceLocationArgument.id())
								.suggests(WORLD_SUGGESTIONS)
								.executes(context -> teleport(context.getSource(),
										ResourceLocationArgument.getId(context, "id"),
										List.of(context.getSource().getPlayerOrException())))
								.then(
										argument("targets", EntityArgument.players())
												.executes(context -> teleport(context.getSource(),
														ResourceLocationArgument.getId(context, "id"),
														EntityArgument.getPlayers(context, "targets")))));
	}

	public static int teleport(CommandSourceStack source, ResourceLocation id, Collection<ServerPlayer> targets)
			throws CommandSyntaxException {
		ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
		ServerLevel serverLevel = source.getServer().getLevel(resourceKey);
		if (serverLevel == null) {
			throw UNKNOWN_WORLD_EXCEPTION.create();
		}
		for (ServerPlayer player : targets) {
			teleport(player, id);
		}
		source.sendSuccess(() -> builder("dev.wroud.mc.worlds.command.teleport.success").addPlaceholder("id", id.toString()).build(),
				false);
		return 1;
	}

	public static boolean teleport(ServerPlayer player, ResourceLocation id) {
		ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
		ServerLevel serverLevel = player.getServer().getLevel(resourceKey);
		if (serverLevel == null || player.level() == serverLevel)
			return false;

		WorldLocation.findSpawn(serverLevel, player).teleport(player);
		return true;
	}
}
