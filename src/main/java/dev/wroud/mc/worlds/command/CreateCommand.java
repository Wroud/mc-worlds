package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.WorldsCreator;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
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
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
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
                    context -> createWorld(context.getSource(), ResourceLocationArgument.getId(context, "id")))
                .then(
                    argument("seed", LongArgumentType.longArg())
                        .executes(
                            context -> createWorld(context.getSource(), ResourceLocationArgument.getId(context, "id"),
                                null,
                                null,
                                LongArgumentType.getLong(context, "seed"))))
                .then(literal("from-preset").then(
                    argument("preset", ResourceKeyArgument.key(Registries.WORLD_PRESET))
                        .executes(
                            context -> createWorld(
                                context.getSource(),
                                ResourceLocationArgument.getId(context, "id"),
                                ResourceKeyArgument.getRegistryKey(context, "preset", Registries.WORLD_PRESET,
                                    ERROR_INVALID_LEVEL_STEM),
                                null,
                                null))
                        .then(
                            argument("seed", LongArgumentType.longArg())
                                .executes(
                                    context -> createWorld(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "id"),
                                        ResourceKeyArgument.getRegistryKey(context, "preset", Registries.WORLD_PRESET,
                                            ERROR_INVALID_LEVEL_STEM),
                                        null,
                                        LongArgumentType.getLong(context, "seed"))))
                        .then(
                            argument("dimension", ResourceLocationArgument.id())
                                .suggests(WorldsCommands.PRESET_DIMENSION_SUGGESTIONS)
                                .executes(
                                    context -> createWorld(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "id"),
                                        ResourceKeyArgument.getRegistryKey(context, "preset", Registries.WORLD_PRESET,
                                            ERROR_INVALID_LEVEL_STEM),
                                        ResourceLocationArgument.getId(context, "dimension"),
                                        null))
                                .then(
                                    argument("seed", LongArgumentType.longArg())
                                        .executes(
                                            context -> createWorld(
                                                context.getSource(),
                                                ResourceLocationArgument.getId(context, "id"),
                                                ResourceKeyArgument.getRegistryKey(context, "preset",
                                                    Registries.WORLD_PRESET,
                                                    ERROR_INVALID_LEVEL_STEM),
                                                ResourceLocationArgument.getId(context, "dimension"),
                                                LongArgumentType.getLong(context, "seed")))))))
                .then(literal("from-dimension").then(
                    argument("dimension", ResourceKeyArgument.key(Registries.LEVEL_STEM))
                        .executes(
                            context -> createWorld(
                                context.getSource(),
                                ResourceLocationArgument.getId(context, "id"),
                                ResourceKeyArgument.getRegistryKey(context, "dimension", Registries.LEVEL_STEM,
                                    ERROR_INVALID_LEVEL_STEM),
                                null))
                        .then(
                            argument("seed", LongArgumentType.longArg())
                                .executes(
                                    context -> createWorld(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "id"),
                                        ResourceKeyArgument.getRegistryKey(context, "dimension", Registries.LEVEL_STEM,
                                            ERROR_INVALID_LEVEL_STEM),
                                        LongArgumentType.getLong(context, "seed")))))));
  }

  public static int createWorld(CommandSourceStack source, ResourceLocation id)
      throws CommandSyntaxException {
    return createWorld(source, id, null, null, null, null);
  }

  public static int createWorld(CommandSourceStack source, ResourceLocation id,
      @Nullable ResourceKey<LevelStem> levelStemKey,
      @Nullable Long seed)
      throws CommandSyntaxException {
    return createWorld(source, id, null, null, levelStemKey, seed);
  }

  public static int createWorld(CommandSourceStack source, ResourceLocation id,
      @Nullable ResourceKey<WorldPreset> preset, @Nullable ResourceLocation dimension,
      @Nullable Long seed)
      throws CommandSyntaxException {
    return createWorld(source, id, preset, dimension, null, seed);
  }

  public static int createWorld(CommandSourceStack source, ResourceLocation id,
      @Nullable ResourceKey<WorldPreset> preset, @Nullable ResourceLocation dimension,
      @Nullable ResourceKey<LevelStem> levelStemKey,
      @Nullable Long seed)
      throws CommandSyntaxException {
    var server = source.getServer();

    try {
      WorldsCreator.createWorld(server, new WorldsCreator.CreationCallbacks() {
        @Override
        public void onCreating(ResourceLocation id, long seed, LevelStem dimension) {

          Component creatingMessage = Component.translatable("dev.wroud.mc.worlds.command.create.creating")
              .append(Component.literal(id.toString()).withStyle(ChatFormatting.GOLD))
              .append(Component.translatable("dev.wroud.mc.worlds.command.create.creating.progress")
                  .withStyle(ChatFormatting.YELLOW));
          source.sendSuccess(() -> creatingMessage, false);

        }

        @Override
        public void onReady(CustomServerLevel level) {
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

        }
      }, id, preset, dimension, levelStemKey, seed);
      return Command.SINGLE_SUCCESS;
    } catch (WorldsCreator.InvalidLevelIdException e) {
      throw new SimpleCommandExceptionType(
          Component.translatable("dev.wroud.mc.worlds.command.create.exception.world_already_exists"))
          .create();
    } catch (Exception e) {
      McWorldMod.LOGGER.error("Error creating world", e);
      throw new SimpleCommandExceptionType(
          Component.translatable("dev.wroud.mc.worlds.command.create.exception.generic", e.getMessage()))
          .create();
    }
  }

}
