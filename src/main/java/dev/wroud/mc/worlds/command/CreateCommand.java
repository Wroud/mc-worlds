package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;

import dev.wroud.mc.worlds.McWorldInitializer;
import dev.wroud.mc.worlds.manadger.LevelData;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;

import static me.drex.message.api.LocalizedMessage.builder;
import static me.drex.message.api.LocalizedMessage.localized;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import org.slf4j.Logger;

public class CreateCommand {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return literal("create")
				.requires(Permissions.require("dev.wroud.mc.worlds.command.create", 2))
				.then(
						argument("id", ResourceLocationArgument.id())
								.executes(context -> createWorld(context.getSource(), ResourceLocationArgument.getId(context, "id"), 0))
								.then(
										argument("seed", LongArgumentType.longArg())
												.executes(
														context -> createWorld(context.getSource(), ResourceLocationArgument.getId(context, "id"),
																LongArgumentType.getLong(context, "seed")))));
	}

	public static int createWorld(CommandSourceStack source, ResourceLocation id, long seed)
			throws CommandSyntaxException {

		LOGGER.info("Creating new world with id: {}, seed: {}", id, seed);
		var server = source.getServer();
		validLevelId(id, server);

		var registry = server.registryAccess();
		var dimensionType = registry
				.lookupOrThrow(Registries.DIMENSION_TYPE);
		var type = dimensionType.getOrThrow(BuiltinDimensionTypes.OVERWORLD);
		var levelStem = new LevelStem(type,
				server.overworld().getChunkSource().getGenerator());
		LOGGER.info("Using dimension type: {}", type);
		var levelData = LevelData.overworldDefault(id, levelStem, seed);

		var handle = McWorldInitializer.getMcWorld(server).loadOrCreate(id,
				levelData);
		LOGGER.info("Created world: {}", id);

		McWorldInitializer.getMcWorld(server).prepareWorld(handle);
		LOGGER.info("Prepared world: {}", id);
		source.sendSuccess(() -> builder("dev.wroud.mc.worlds.command.create.success")
				.addPlaceholder("id", id.toString()).build(), false);

		return Command.SINGLE_SUCCESS;
	}

	public static void validLevelId(ResourceLocation id, MinecraftServer server) throws CommandSyntaxException {
		ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
		ServerLevel level = server.getLevel(resourceKey);
		if (level != null) {
			throw new SimpleCommandExceptionType(localized("dev.wroud.mc.worlds.command.create.exception.world_already_exists"))
					.create();
		}
	}
}
