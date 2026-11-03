package dev.wroud.mc.worlds.command;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.wroud.mc.worlds.McWorldMod;
import dev.wroud.mc.worlds.manager.WorldsException;
import dev.wroud.mc.worlds.manager.importer.ArchiveDownload;
import dev.wroud.mc.worlds.manager.importer.ImportException;
import dev.wroud.mc.worlds.manager.importer.SourceSave;
import dev.wroud.mc.worlds.manager.importer.ImportResult;
import dev.wroud.mc.worlds.mixin.MinecraftServerAccessor;
import net.minecraft.commands.CommandSourceStack;
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
        .then(literal("temporary")
            .then(WorldsCommands.temporaryNameArgument()
                .then(sources((context, folder, dimension, all, group) -> start(context.getSource(),
                    WorldsCommands.temporaryId(context), folder, dimension, false, group)))))
        .then(argument("id", IdentifierArgument.id())
            .then(sources(ImportCommand::ordinary)
                .then(literal("all")
                    .executes(run(ImportCommand::ordinary, false, true, false))
                    .then(group(run(ImportCommand::ordinary, false, true, true))))));
  }

  private static int ordinary(CommandContext<CommandSourceStack> context, String folder,
      @Nullable Identifier dimension, boolean all, @Nullable Identifier group) throws CommandSyntaxException {
    return importWorld(context.getSource(), IdentifierArgument.getId(context, "id"), folder, dimension, all, group);
  }

  private static RequiredArgumentBuilder<CommandSourceStack, String> sources(Target target) {
    return argument("folder", StringArgumentType.string())
        .suggests(FOLDER_SUGGESTIONS)
        .executes(run(target, false, false, false))
        .then(group(run(target, false, false, true)))
        .then(
            argument("dimension", IdentifierArgument.id())
                .suggests(DIMENSION_SUGGESTIONS)
                .executes(run(target, true, false, false))
                .then(group(run(target, true, false, true))));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> group(Command<CommandSourceStack> command) {
    return literal("group")
        .then(WorldsCommands.groupArgument().executes(command));
  }

  private static Command<CommandSourceStack> run(Target target, boolean withDimension, boolean all,
      boolean withGroup) {
    return context -> target.run(context, StringArgumentType.getString(context, "folder"),
        withDimension ? IdentifierArgument.getId(context, "dimension") : null, all,
        withGroup ? IdentifierArgument.getId(context, "group") : null);
  }

  @FunctionalInterface
  private interface Target {
    int run(CommandContext<CommandSourceStack> context, String folder, @Nullable Identifier dimension, boolean all,
        @Nullable Identifier group) throws CommandSyntaxException;
  }

  public static int importWorld(CommandSourceStack source, Identifier id, String folder,
      @Nullable Identifier dimension, boolean all, @Nullable Identifier group) throws CommandSyntaxException {
    WorldsCommands.refuseReserved(id);
    return start(source, id, folder, dimension, all, group);
  }

  private static int start(CommandSourceStack source, Identifier id, String folder, @Nullable Identifier dimension,
      boolean all, @Nullable Identifier group) throws CommandSyntaxException {
    var label = ArchiveDownload.label(folder);
    var future = WorldsCommands.call(() -> WorldsCommands.worlds(source).importWorld(id, folder, dimension, all, group,
        targets -> {
          var ids = targets.stream().map(Identifier::toString).collect(Collectors.joining(", "));
          McWorldMod.LOGGER.info("Importing {} from {}", ids, label);
          source.sendSuccess(() -> Component.translatable(ImportException.KEY_PREFIX + "started", ids, label), true);
        }));
    future.whenComplete((result, error) -> report(source, label, result, error));
    return Command.SINGLE_SUCCESS;
  }

  private static void report(CommandSourceStack source, String folder, @Nullable ImportResult result,
      @Nullable Throwable error) {
    if (error instanceof WorldsException failure) {
      McWorldMod.LOGGER.warn("Import from {} failed: {}", folder, failure.getMessage());
      source.sendFailure(failure.toComponent());
      return;
    }

    for (var line : result.lines()) {
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

}
