package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.wroud.mc.worlds.McWorld;
import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.manager.WorldsException;
import dev.wroud.mc.worlds.manager.WorldsManager;
import dev.wroud.mc.worlds.mixin.WorldPresetAccessor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandPermissionLevels;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.StringArgument;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.commands.suggestions.SuggestionProviders;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.function.Function;
import java.util.function.Supplier;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class WorldsCommands {
  public static final SuggestionProvider<CommandSourceStack> PRESET_DIMENSION_SUGGESTIONS = SuggestionProviders
      .serverOnly((server, context, builder) -> {
        var worldPreset = ResourceArgument.getResource(context, "preset", Registries.WORLD_PRESET).value();
        var dimensions = ((WorldPresetAccessor) worldPreset).getDimensions().keySet().stream()
            .filter(key -> !key.equals(LevelStem.NETHER) && !key.equals(LevelStem.END)).map(ResourceKey::identifier);
        return SuggestionContext.suggestResource(dimensions, builder);
      });

  public static final SuggestionProvider<CommandSourceStack> WORLD_SUGGESTIONS = suggestIds(WorldsManager::allWorldIds);

  public static final SuggestionProvider<CommandSourceStack> CUSTOM_WORLD_SUGGESTIONS = suggestIds(
      WorldsManager::getWorldIds);

  public static final SuggestionProvider<CommandSourceStack> GROUP_SUGGESTIONS = suggestIds(
      manager -> manager.getGroups().all().keySet());

  private static SuggestionProvider<CommandSourceStack> suggestIds(
      Function<WorldsManager, Iterable<Identifier>> ids) {
    return SuggestionProviders.serverOnly((server, context, builder) -> SuggestionContext
        .suggestResource(ids.apply(McWorldMod.getManager(server).orElseThrow()), builder));
  }

  public static RequiredArgumentBuilder<CommandSourceStack, Identifier> groupArgument() {
    return argument("group", IdentifierArgument.id()).suggests(GROUP_SUGGESTIONS);
  }

  public static McWorld worlds(CommandSourceStack source) {
    return McWorld.of(source.getServer());
  }

  public static WorldsManager manager(CommandSourceStack source) {
    return McWorldMod.getManager(source.getServer()).orElseThrow();
  }

  public static CommandSyntaxException failure(Component message) {
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
    return argument("name", StringArgument.word());
  }

  public static Identifier temporaryId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    var name = StringArgument.getString(context, "name");
    return call(() -> McWorld.temporaryId(name));
  }

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
      CommandBuildContext commandBuildContext) {
    dispatcher.register(
        literal("worlds")
            .requires(Commands.hasPermission(CommandPermissionLevels.LEVEL_GAMEMASTERS))
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
