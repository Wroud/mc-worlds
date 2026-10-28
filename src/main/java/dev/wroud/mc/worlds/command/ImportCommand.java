package dev.wroud.mc.worlds.command;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.WorldsCreator;
import dev.wroud.mc.worlds.manager.importer.ArchiveDownload;
import dev.wroud.mc.worlds.manager.importer.ImportException;
import dev.wroud.mc.worlds.manager.importer.SourceSave;
import dev.wroud.mc.worlds.manager.importer.WorldImporter;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ImportCommand {
  private static final SuggestionProvider<CommandSourceStack> FOLDER_SUGGESTIONS = (context, builder) -> {
    var access = storage(context.getSource().getServer());
    return CompletableFuture.supplyAsync(() -> SourceSave.suggestFolders(access), Util.ioPool())
        .thenCompose(folders -> SharedSuggestionProvider.suggest(
            folders.stream().map(StringArgumentType::escapeIfRequired), builder));
  };

  private static final SuggestionProvider<CommandSourceStack> DIMENSION_SUGGESTIONS = (context, builder) -> {
    var access = storage(context.getSource().getServer());
    var folder = StringArgumentType.getString(context, "folder");
    return CompletableFuture.supplyAsync(() -> SourceSave.suggestDimensions(access, folder), Util.ioPool())
        .thenCompose(dimensions -> SharedSuggestionProvider.suggestResource(dimensions, builder));
  };

  public static LiteralArgumentBuilder<CommandSourceStack> build() {
    return literal("import")
        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
        .then(
            argument("id", IdentifierArgument.id())
                .then(
                    argument("folder", StringArgumentType.string())
                        .suggests(FOLDER_SUGGESTIONS)
                        .executes(run(false, false))
                        .then(literal("all").executes(run(false, true)))
                        .then(
                            argument("dimension", IdentifierArgument.id())
                                .suggests(DIMENSION_SUGGESTIONS)
                                .executes(run(true, false)))));
  }

  private static Command<CommandSourceStack> run(boolean withDimension, boolean all) {
    return context -> importWorld(context.getSource(), IdentifierArgument.getId(context, "id"),
        StringArgumentType.getString(context, "folder"),
        withDimension ? IdentifierArgument.getId(context, "dimension") : null, all);
  }

  public static int importWorld(CommandSourceStack source, Identifier id, String folder,
      @Nullable Identifier dimension, boolean all) throws CommandSyntaxException {
    var server = source.getServer();
    var running = McWorldMod.getMcWorld(server).orElseThrow().currentImport();
    if (running.isPresent()) {
      throw failure(new ImportException("exception.busy", running.get()).toComponent());
    }
    try {
      WorldsCreator.validLevelIdFormat(id);
    } catch (WorldsCreator.InvalidLevelIdException e) {
      throw failure(Component.translatable(e.getTranslationKey(), id.toString()));
    }

    var label = ArchiveDownload.label(folder);
    WorldImporter.run(server, id, folder, label, dimension, all, targets -> {
      var ids = targets.stream().map(Identifier::toString).collect(Collectors.joining(", "));
      McWorldMod.LOGGER.info("Importing {} from {}", ids, label);
      source.sendSuccess(() -> Component.translatable(ImportException.KEY_PREFIX + "started", ids, label), true);
    }).whenCompleteAsync((lines, error) -> report(source, label, lines, error), server);
    return Command.SINGLE_SUCCESS;
  }

  private static void report(CommandSourceStack source, String folder, List<WorldImporter.Line> lines,
      @Nullable Throwable error) {
    if (error != null) {
      var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
      Component message;
      if (cause instanceof ImportException importException) {
        message = importException.toComponent();
        McWorldMod.LOGGER.warn("Import from {} failed: {}", folder, message.getString());
      } else {
        McWorldMod.LOGGER.error("Import from {} failed", folder, cause);
        message = ImportException.failed(folder, cause).toComponent();
      }
      source.sendFailure(message);
      return;
    }

    for (var line : lines) {
      if (line.failure()) {
        McWorldMod.LOGGER.error("Import from {}: {}", folder, line.message().getString());
        source.sendFailure(line.message());
      } else {
        McWorldMod.LOGGER.info("Import from {}: {}", folder, line.message().getString());
        source.sendSuccess(line::message, true);
      }
    }
  }

  private static LevelStorageSource.LevelStorageAccess storage(MinecraftServer server) {
    return ((MinecraftServerAccessor) server).getStorageSource();
  }

  private static CommandSyntaxException failure(Component message) {
    return new SimpleCommandExceptionType(message).create();
  }
}
