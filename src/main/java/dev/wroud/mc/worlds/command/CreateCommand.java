package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.util.LevelActivationUtil;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import org.jetbrains.annotations.Nullable;

public class CreateCommand {
  private static final DynamicCommandExceptionType ERROR_INVALID_LEVEL_STEM = new DynamicCommandExceptionType(
      object -> Component.translatableEscape("advancement.advancementNotFound", object));

  public static LiteralArgumentBuilder<CommandSourceStack> build() {
    return literal("create")
        .requires(Permissions.require("dev.wroud.mc.worlds.command.create", 2))
        .then(
            argument("id", ResourceLocationArgument.id())
                .executes(
                    context -> createWorld(context.getSource(), ResourceLocationArgument.getId(context, "id"), null,
                        null))
                .then(
                    argument("type", ResourceKeyArgument.key(Registries.LEVEL_STEM))
                        .executes(
                            context -> createWorld(
                                context.getSource(),
                                ResourceLocationArgument.getId(context, "id"),
                                ResourceKeyArgument.getRegistryKey(context, "type", Registries.LEVEL_STEM,
                                    ERROR_INVALID_LEVEL_STEM),
                                null))
                        .then(
                            argument("seed", LongArgumentType.longArg())
                                .executes(
                                    context -> createWorld(context.getSource(),
                                        ResourceLocationArgument.getId(context, "id"),
                                        ResourceKeyArgument.getRegistryKey(context, "type", Registries.LEVEL_STEM,
                                            ERROR_INVALID_LEVEL_STEM),
                                        LongArgumentType.getLong(context, "seed")))))
                .then(
                    argument("seed", LongArgumentType.longArg())
                        .executes(
                            context -> createWorld(context.getSource(), ResourceLocationArgument.getId(context, "id"),
                                null,
                                LongArgumentType.getLong(context, "seed")))));
  }

  public static int createWorld(CommandSourceStack source, ResourceLocation id,
      @Nullable ResourceKey<LevelStem> type, @Nullable Long seed)
      throws CommandSyntaxException {

    if (type == null) {
      type = LevelStem.OVERWORLD;
    }

    if (seed == null) {
      seed = WorldOptions.randomSeed();
    }

    McWorldMod.LOGGER.info("Creating new world with id: {}, seed: {}, type: {}", id, seed, type.location());
    var server = source.getServer();
    validLevelId(id, server);

    try {
      var registry = server.registryAccess();
      var levelStemRegistry = registry.lookupOrThrow(Registries.LEVEL_STEM);

      var levelStem = levelStemRegistry.getValue(type);
      var levelData = WorldsLevelData.getDefault(id, levelStem, seed, true);

      Component creatingMessage = Component.translatable("dev.wroud.mc.worlds.command.create.creating")
          .append(Component.literal(id.toString()).withStyle(ChatFormatting.GOLD))
          .append(Component.translatable("dev.wroud.mc.worlds.command.create.creating.progress")
              .withStyle(ChatFormatting.YELLOW));
      source.sendSuccess(() -> creatingMessage, false);

      var worldHandle = McWorldMod.getMcWorld(server).loadOrCreate(id, levelData);

      LevelActivationUtil.executeWhenLevelReady(worldHandle.getServerLevel(), () -> {
        Component successMessage = Component.translatable("dev.wroud.mc.worlds.command.create.success")
            .append(Component.literal(id.toString()).withStyle(ChatFormatting.GOLD))
            .append(Component.translatable("dev.wroud.mc.worlds.command.create.success.created"))
            .append(Component.translatable("dev.wroud.mc.worlds.command.create.success.teleport")
                .withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand(
                        "/worlds tp " + id.toString()))));

        source.sendSystemMessage(successMessage);
      });

      return Command.SINGLE_SUCCESS;
    } catch (Exception e) {
      McWorldMod.LOGGER.error("Error creating world", e);
      throw new SimpleCommandExceptionType(
          Component.translatable("dev.wroud.mc.worlds.command.create.exception.generic", e.getMessage()))
          .create();
    }
  }

  public static void validLevelId(ResourceLocation id, MinecraftServer server) throws CommandSyntaxException {
    ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    ServerLevel level = server.getLevel(resourceKey);
    if (level != null) {
      throw new SimpleCommandExceptionType(
          Component.translatable("dev.wroud.mc.worlds.command.create.exception.world_already_exists"))
          .create();
    }
  }

}
