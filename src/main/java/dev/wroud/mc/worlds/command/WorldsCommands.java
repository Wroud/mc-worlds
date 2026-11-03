package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.wroud.mc.worlds.McWorld;
import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.manager.WorldsException;
import dev.wroud.mc.worlds.manager.WorldsManager;
import dev.wroud.mc.worlds.mixin.WorldPresetAccessor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.function.Supplier;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class WorldsCommands {
  private static final DynamicCommandExceptionType ERROR_INVALID_PRESET = new DynamicCommandExceptionType(
      object -> Component.translatableEscape("advancement.advancementNotFound", object));

  public static final SuggestionProvider<CommandSourceStack> PRESET_DIMENSION_SUGGESTIONS = (context,
      builder) -> {
    var preset = ResourceKeyArgument.getRegistryKey(context, "preset", Registries.WORLD_PRESET,
        ERROR_INVALID_PRESET);
    var worldPreset = context.getSource().registryAccess().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(preset)
        .value();
    var dimensions = ((WorldPresetAccessor) worldPreset).getDimensions().keySet().stream()
        .filter(key -> !key.equals(LevelStem.NETHER) && !key.equals(LevelStem.END)).map(ResourceKey::identifier);

    return SharedSuggestionProvider.suggestResource(dimensions, builder);
  };

  public static final SuggestionProvider<CommandSourceStack> WORLD_SUGGESTIONS = (context,
      builder) -> SharedSuggestionProvider.suggestResource(manager(context.getSource()).allWorldIds(), builder);

  public static final SuggestionProvider<CommandSourceStack> CUSTOM_WORLD_SUGGESTIONS = (context,
      builder) -> SharedSuggestionProvider.suggestResource(manager(context.getSource()).getWorldIds(), builder);

  public static final SuggestionProvider<CommandSourceStack> GROUP_SUGGESTIONS = (context,
      builder) -> SharedSuggestionProvider.suggestResource(manager(context.getSource()).getGroups().all().keySet(),
          builder);

  public static RequiredArgumentBuilder<CommandSourceStack, Identifier> groupArgument() {
    return argument("group", IdentifierArgument.id()).suggests(GROUP_SUGGESTIONS);
  }

  public static McWorld worlds(CommandSourceStack source) {
    return McWorld.of(source.getServer());
  }

  public static WorldsManager manager(CommandSourceStack source) {
    return McWorldMod.getManager(source.getServer()).orElseThrow();
  }

  private static CommandSyntaxException failure(Component message) {
    return new SimpleCommandExceptionType(message).create();
  }

  public static <T> T call(Supplier<T> operation) throws CommandSyntaxException {
    try {
      return operation.get();
    } catch (WorldsException e) {
      throw failure(e.toComponent());
    }
  }

  public static void call(Runnable operation) throws CommandSyntaxException {
    call(() -> {
      operation.run();
      return null;
    });
  }

  public static void refuseReserved(Identifier id) throws CommandSyntaxException {
    if (TemporaryIds.isTemporary(id)) {
      throw failure(Component.translatableEscape("dev.wroud.mc.worlds.command.exception.reserved_namespace", id));
    }
  }

  public static RequiredArgumentBuilder<CommandSourceStack, String> temporaryNameArgument() {
    return argument("name", StringArgumentType.word());
  }

  public static Identifier temporaryId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    var name = StringArgumentType.getString(context, "name");
    return call(() -> McWorld.temporaryId(name));
  }

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
      CommandBuildContext commandBuildContext) {
    dispatcher.register(
        literal("worlds")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(DeleteCommand.build())
            .then(TeleportCommand.build())
            .then(CreateCommand.build())
            .then(SettingsCommand.build())
            .then(ListCommand.build())
            .then(ImportCommand.build())
            .then(GroupCommand.build()));

    WorldGameRuleCommand.register(dispatcher, commandBuildContext);
  }
}
