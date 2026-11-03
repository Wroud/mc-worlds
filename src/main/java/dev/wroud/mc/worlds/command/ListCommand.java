package dev.wroud.mc.worlds.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import dev.wroud.mc.worlds.manager.WorldHandle;
import dev.wroud.mc.worlds.manager.level.data.WorldsLevelData;
import dev.wroud.mc.worlds.manager.LazyLevelMap;
import dev.wroud.mc.worlds.manager.TemporaryIds;
import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import static net.minecraft.commands.Commands.literal;

public class ListCommand {
  private static final String KEY = "dev.wroud.mc.worlds.command.list.";

  public static LiteralArgumentBuilder<CommandSourceStack> build() {
    return literal("list")
        .executes(context -> list(context.getSource(), true, true))
        .then(literal("loaded")
            .executes(context -> list(context.getSource(), true, false)))
        .then(literal("unloaded")
            .executes(context -> list(context.getSource(), false, true)));
  }

  public static int list(CommandSourceStack source, boolean showLoaded, boolean showUnloaded) {
    var server = source.getServer();
    var manager = WorldsCommands.manager(source);
    var groups = manager.getGroups();
    var levels = LazyLevelMap.loaded(server);
    var saved = manager.getWorldsData().getLevelsData();

    var ids = manager.allWorldIds();

    Map<ResourceKey<Level>, List<ServerPlayer>> playersByDimension = server.getPlayerList().getPlayers().stream()
        .collect(Collectors.groupingBy(player -> player.level().dimension()));
    var spawnDimension = server.getRespawnData().dimension();

    var loaded = new ArrayList<Component>();
    var unloaded = new ArrayList<Component>();

    for (var id : ids) {
      var key = ResourceKey.create(Registries.DIMENSION, id);
      ServerLevel level = levels.get(key);
      if (level == null) {
        WorldHandle handle = manager.getWorld(id);
        if (handle != null) {
          level = handle.getServerLevel();
        }
      }

      if (level instanceof CustomServerLevel customLevel && customLevel.isDeleteOnClose()) {
        continue;
      }

      var isLoaded = level != null;
      if (isLoaded ? !showLoaded : !showUnloaded) {
        continue;
      }

      var data = saved.get(id);
      Holder<DimensionType> type = isLoaded
          ? level.dimensionTypeRegistration()
          : data != null && data.getLevelStem() != null ? data.getLevelStem().type() : null;

      var details = details(id, isLoaded, type, data, playersByDimension.getOrDefault(key, List.of()),
          key.equals(spawnDimension), groups.groupOrDefault(id),
          groups.spawnHolder(id));
      var entry = entry(id, isLoaded, details);

      (isLoaded ? loaded : unloaded).add(entry);
    }

    var count = 0;
    if (showLoaded) {
      count += send(source, "loaded", loaded);
    }
    if (showUnloaded) {
      count += send(source, "unloaded", unloaded);
    }
    return count;
  }

  private static int send(CommandSourceStack source, String group, List<Component> entries) {
    if (entries.isEmpty()) {
      source.sendSuccess(() -> Component.translatable(KEY + group + ".none"), false);
    } else {
      source.sendSuccess(() -> Component.translatable(KEY + group + ".success", entries.size(),
          ComponentUtils.formatList(entries, ComponentUtils.DEFAULT_SEPARATOR)), false);
    }
    return entries.size();
  }

  private static Component entry(Identifier id, boolean loaded, Component details) {
    return ComponentUtils.wrapInSquareBrackets(Component.literal(id.toString()))
        .withStyle(style -> style
            .withColor(statusColor(loaded))
            .withInsertion(id.toString())
            .withHoverEvent(new HoverEvent.ShowText(details))
            .withClickEvent(new ClickEvent.SuggestCommand(TeleportCommand.command(id))));
  }

  private static Component details(Identifier id, boolean loaded, Holder<DimensionType> type, WorldsLevelData data,
      List<ServerPlayer> players, boolean worldSpawn, Identifier group, @Nullable Identifier groupSpawn) {
    var lines = new ArrayList<Component>();

    lines.add(Component.literal(id.toString()).withStyle(ChatFormatting.BOLD));
    lines.add(Component.translatable(KEY + (loaded ? "status.loaded" : "status.unloaded"))
        .withStyle(statusColor(loaded)));
    if (TemporaryIds.isTemporary(id)) {
      lines.add(Component.translatable(KEY + "temporary").withStyle(ChatFormatting.AQUA));
    }
    lines.add(CommonComponents.EMPTY);

    if (type != null) {
      type.unwrapKey().ifPresent(typeKey -> lines.add(field("dimension_type",
          Component.literal(typeKey.identifier().toString()))));
    }

    if (data != null) {
      lines.add(field("provider", Component.literal(data.getProvider().identifier().toString())));
      lines.add(field("load_on_startup", data.isLazy() ? CommonComponents.GUI_NO : CommonComponents.GUI_YES));
    }

    lines.add(field("group", Component.literal(group.toString())));
    lines.add(field("players", players.isEmpty()
        ? Component.translatable(KEY + "players.none")
        : ComponentUtils.formatList(players, ServerPlayer::getName)));

    if (worldSpawn) {
      lines.add(Component.translatable(KEY + "world_spawn").withStyle(ChatFormatting.GOLD));
    }
    if (groupSpawn != null) {
      lines.add(Component.translatable(KEY + "group_spawn", groupSpawn.toString()).withStyle(ChatFormatting.GOLD));
    }

    lines.add(CommonComponents.EMPTY);
    lines.add(Component.translatable(KEY + "click", TeleportCommand.command(id))
        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

    return ComponentUtils.formatList(lines, CommonComponents.NEW_LINE);
  }

  private static Component field(String name, Component value) {
    return Component.translatable(KEY + name, value.copy().withStyle(ChatFormatting.WHITE))
        .withStyle(ChatFormatting.GRAY);
  }

  private static ChatFormatting statusColor(boolean loaded) {
    return loaded ? ChatFormatting.GREEN : ChatFormatting.GRAY;
  }
}
