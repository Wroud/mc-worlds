package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static dev.wroud.mc.worlds.command.WorldsCommands.CUSTOM_WORLD_SUGGESTIONS;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DeleteCommand {
	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return literal("delete")
				.then(
						argument("id", IdentifierArgument.id())
								.suggests(CUSTOM_WORLD_SUGGESTIONS)
								.executes(context -> delete(context.getSource(),
										IdentifierArgument.getId(context, "id"))));
	}

	public static int delete(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
		var manager = WorldsCommands.manager(source);
		var groups = manager.getGroups();
		var handle = manager.getWorld(id);
		var wasRespawnLevel = handle != null && handle.getServerLevel().isRespawnLevel();
		var spawnGroup = groups.spawnHolder(id);
		WorldsCommands.call(() -> WorldsCommands.worlds(source).deleteWorld(id));

		if (spawnGroup != null) {
			var spawn = groups.spawnOf(spawnGroup).spawn();
			source.sendSuccess(
					() -> Component.translatable("dev.wroud.mc.worlds.command.delete.group_spawn_reset",
							spawnGroup.toString(), id.toString(), spawn.dimension().identifier().toString(),
							spawn.pos().toShortString()),
					true);
		}

		if (wasRespawnLevel) {
			reportSpawnReset(source, id);
		}

		source.sendSuccess(
				() -> Component.translatable("dev.wroud.mc.worlds.command.delete.success", id.toString()),
				false);
		return 1;
	}

	static void reportSpawnReset(CommandSourceStack source, Identifier world) {
		var spawnPos = source.getServer().getRespawnData().pos();
		source.sendSuccess(
				() -> Component.translatable("dev.wroud.mc.worlds.command.delete.spawn_reset", world.toString(),
						spawnPos.toShortString()),
				true);
	}
}
