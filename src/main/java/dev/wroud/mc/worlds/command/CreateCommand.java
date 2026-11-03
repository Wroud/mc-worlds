package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import dev.wroud.mc.worlds.manager.WorldsException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.IdentifierArgument;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import org.jetbrains.annotations.Nullable;

public class CreateCommand {
  private static final DynamicCommandExceptionType ERROR_INVALID_LEVEL_STEM = new DynamicCommandExceptionType(
      object -> Component.translatableEscape("advancement.advancementNotFound", object));

  @FunctionalInterface
  private interface Creation {
    int run(CommandContext<CommandSourceStack> context, @Nullable ResourceKey<WorldPreset> preset,
        @Nullable Identifier dimension, @Nullable ResourceKey<LevelStem> levelStemKey, @Nullable Long seed)
        throws CommandSyntaxException;
  }

  public static LiteralArgumentBuilder<CommandSourceStack> build() {
    return literal("create")
        .then(literal("temporary")
            .then(sources(WorldsCommands.temporaryNameArgument(),
                (context, preset, dimension, levelStemKey, seed) -> create(context.getSource(),
                    WorldsCommands.temporaryId(context), preset, dimension, levelStemKey, seed))))
        .then(sources(argument("id", IdentifierArgument.id()),
            (context, preset, dimension, levelStemKey, seed) -> createWorld(context.getSource(),
                IdentifierArgument.getId(context, "id"), preset, dimension, levelStemKey, seed)));
  }

  private static <T extends ArgumentBuilder<CommandSourceStack, T>> T sources(T target, Creation creation) {
    return target
        .executes(context -> creation.run(context, null, null, null, null))
        .then(argument("seed", LongArgumentType.longArg())
            .executes(context -> creation.run(context, null, null, null, seed(context))))
        .then(literal("from-preset").then(
            argument("preset", ResourceKeyArgument.key(Registries.WORLD_PRESET))
                .executes(context -> creation.run(context, preset(context), null, null, null))
                .then(argument("seed", LongArgumentType.longArg())
                    .executes(context -> creation.run(context, preset(context), null, null,
                        seed(context))))
                .then(argument("dimension", IdentifierArgument.id())
                    .suggests(WorldsCommands.PRESET_DIMENSION_SUGGESTIONS)
                    .executes(context -> creation.run(context, preset(context),
                        IdentifierArgument.getId(context, "dimension"), null, null))
                    .then(argument("seed", LongArgumentType.longArg())
                        .executes(context -> creation.run(context, preset(context),
                            IdentifierArgument.getId(context, "dimension"), null,
                            seed(context)))))))
        .then(literal("from-dimension").then(
            argument("dimension", ResourceKeyArgument.key(Registries.LEVEL_STEM))
                .executes(context -> creation.run(context, null, null, levelStem(context), null))
                .then(argument("seed", LongArgumentType.longArg())
                    .executes(context -> creation.run(context, null, null, levelStem(context),
                        seed(context))))));
  }

  private static long seed(CommandContext<CommandSourceStack> context) {
    return LongArgumentType.getLong(context, "seed");
  }

  private static ResourceKey<WorldPreset> preset(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    return ResourceKeyArgument.getRegistryKey(context, "preset", Registries.WORLD_PRESET, ERROR_INVALID_LEVEL_STEM);
  }

  private static ResourceKey<LevelStem> levelStem(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    return ResourceKeyArgument.getRegistryKey(context, "dimension", Registries.LEVEL_STEM, ERROR_INVALID_LEVEL_STEM);
  }

  public static int createWorld(CommandSourceStack source, Identifier id,
      @Nullable ResourceKey<WorldPreset> preset, @Nullable Identifier dimension,
      @Nullable ResourceKey<LevelStem> levelStemKey,
      @Nullable Long seed)
      throws CommandSyntaxException {
    WorldsCommands.refuseReserved(id);
    return create(source, id, preset, dimension, levelStemKey, seed);
  }

  private static int create(CommandSourceStack source, Identifier id,
      @Nullable ResourceKey<WorldPreset> preset, @Nullable Identifier dimension,
      @Nullable ResourceKey<LevelStem> levelStemKey,
      @Nullable Long seed)
      throws CommandSyntaxException {
    var worlds = WorldsCommands.worlds(source);
    var future = WorldsCommands.call(() -> worlds.createWorld(id,
        levelStemKey != null ? worlds.levelStem(levelStemKey) : worlds.levelStem(preset, dimension), seed));
    reportCreating(source, id);
    future.whenComplete((level, error) -> {
      if (error instanceof WorldsException failure) {
        source.sendFailure(failure.toComponent());
      } else if (error != null) {
        source.sendFailure(
            Component.translatable("dev.wroud.mc.worlds.command.create.exception.generic", error.getMessage()));
      } else {
        reportCreated(source, id);
      }
    });
    return Command.SINGLE_SUCCESS;
  }

  private static void reportCreating(CommandSourceStack source, Identifier id) {
    Component creatingMessage = Component.translatable("dev.wroud.mc.worlds.command.create.creating")
        .append(Component.literal(id.toString()).withStyle(ChatFormatting.GOLD))
        .append(Component.translatable("dev.wroud.mc.worlds.command.create.creating.progress")
            .withStyle(ChatFormatting.YELLOW));
    source.sendSuccess(() -> creatingMessage, false);
  }

  private static void reportCreated(CommandSourceStack source, Identifier id) {
    Component successMessage = Component.translatable("dev.wroud.mc.worlds.command.create.success")
        .append(Component.literal(id.toString()).withStyle(ChatFormatting.GOLD))
        .append(Component.translatable("dev.wroud.mc.worlds.command.create.success.created"))
        .append(TeleportCommand.link(id));
    source.sendSystemMessage(successMessage);
  }
}
