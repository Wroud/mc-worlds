package dev.wroud.mc.worlds.command;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.wroud.mc.worlds.McWorld;
import dev.wroud.mc.worlds.manager.group.WorldGroup;
import dev.wroud.mc.worlds.manager.group.WorldGroups;
import dev.wroud.mc.worlds.manager.group.WorldKind;
import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class GroupCommand {
  private static final String KEY = "dev.wroud.mc.worlds.command.group.";

  public static LiteralArgumentBuilder<CommandSourceStack> build() {
    return literal("group")
        .executes(context -> info(context.getSource()))
        .then(GroupTempCommand.build())
        .then(literal("create")
            .then(literal("temporary")
                .then(WorldsCommands.temporaryNameArgument()
                    .executes(context -> createGroup(context.getSource(), WorldsCommands.temporaryId(context)))))
            .then(argument("group", IdentifierArgument.id())
                .executes(context -> create(context.getSource(), IdentifierArgument.getId(context, "group")))))
        .then(literal("delete")
            .then(WorldsCommands.groupArgument()
                .executes(context -> delete(context.getSource(), IdentifierArgument.getId(context, "group")))))
        .then(literal("join")
            .then(WorldsCommands.groupArgument()
                .executes(context -> join(context.getSource(), IdentifierArgument.getId(context, "group"), false))
                .then(literal("main")
                    .executes(context -> join(context.getSource(), IdentifierArgument.getId(context, "group"), true)))))
        .then(literal("leave")
            .executes(context -> leave(context.getSource())))
        .then(literal("spawn")
            .executes(context -> getSpawn(context.getSource()))
            .then(literal("here")
                .executes(context -> setSpawn(context.getSource(), null)))
            .then(argument("position", Vec3Argument.vec3())
                .executes(context -> setSpawn(context.getSource(), Vec3Argument.getVec3(context, "position")))))
        .then(literal("list")
            .executes(context -> list(context.getSource())));
  }

  public static int info(CommandSourceStack source) {
    var level = source.getLevel();
    var groups = WorldsCommands.manager(source).getGroups();
    var group = McWorld.groupOf(level);

    var lines = new ArrayList<Component>();
    lines.add(Component.translatable(KEY + "query", id(level.dimension().identifier()), id(group)));
    lines.addAll(describe(groups, group));

    source.sendSuccess(() -> ComponentUtils.formatList(lines, CommonComponents.NEW_LINE), false);
    return Command.SINGLE_SUCCESS;
  }

  public static int create(CommandSourceStack source, Identifier group) throws CommandSyntaxException {
    WorldsCommands.refuseReserved(group);
    return createGroup(source, group);
  }

  private static int createGroup(CommandSourceStack source, Identifier group) throws CommandSyntaxException {
    WorldsCommands.call(() -> WorldsCommands.worlds(source).createGroup(group));
    source.sendSuccess(() -> Component.translatable(KEY + "create.success", id(group)), false);
    return Command.SINGLE_SUCCESS;
  }

  public static int delete(CommandSourceStack source, Identifier group) throws CommandSyntaxException {
    var spawnWorld = source.getServer().getRespawnData().dimension();
    var target = require(source, group);
    var count = target.members().size();
    WorldsCommands.call(target::delete);

    if (!spawnWorld.equals(source.getServer().getRespawnData().dimension())) {
      DeleteCommand.reportSpawnReset(source, spawnWorld.identifier());
    }
    var message = McWorld.isTemporary(group) ? "delete.temporary_success" : "delete.success";
    source.sendSuccess(() -> Component.translatable(KEY + message, id(group), count), false);
    return Command.SINGLE_SUCCESS;
  }

  public static int join(CommandSourceStack source, Identifier group, boolean asMain) throws CommandSyntaxException {
    var level = source.getLevel();
    var world = level.dimension().identifier();
    var target = require(source, group);
    var result = WorldsCommands.call(() -> target.join(world, asMain));

    var role = Component.translatable(KEY + "role." + (result.main() ? "main." : "member.") + result.kind().key());
    var previous = result.previousGroup();
    var lines = new ArrayList<Component>();
    if (group.equals(previous)) {
      lines.add(Component.translatable(KEY + "join.changed", id(world), role, id(group)));
    } else if (previous == null) {
      lines.add(Component.translatable(KEY + "join.success", id(world), id(group), role));
    } else {
      lines.add(Component.translatable(KEY + "join.moved", id(world), id(previous), id(group), role));
    }
    if (result.replacedMain() != null) {
      lines.add(Component.translatable(KEY + "join.replaced_main", id(result.replacedMain()), role, id(group)));
    }

    var paused = previous == null ? PerWorldGameRules.ownOverrides(level) : 0;
    if (paused > 0) {
      lines.add(Component.translatable(KEY + "join.paused_rules", paused));
    }

    source.sendSuccess(() -> ComponentUtils.formatList(lines, CommonComponents.NEW_LINE), false);
    return Command.SINGLE_SUCCESS;
  }

  public static int leave(CommandSourceStack source) throws CommandSyntaxException {
    var level = source.getLevel();
    var world = level.dimension().identifier();
    var previous = McWorld.groupOf(level);
    if (WorldGroups.isDefault(previous)) {
      throw WorldsCommands.failure(Component.translatable(KEY + "leave.exception.default", id(world)));
    }
    var target = require(source, previous);
    WorldsCommands.call(() -> target.leave(world));

    var lines = new ArrayList<Component>();
    lines.add(Component.translatable(KEY + "leave.success", id(world), id(previous)));
    var resumed = PerWorldGameRules.ownOverrides(level);
    if (resumed > 0) {
      lines.add(Component.translatable(KEY + "leave.resumed_rules", resumed));
    }

    source.sendSuccess(() -> ComponentUtils.formatList(lines, CommonComponents.NEW_LINE), false);
    return Command.SINGLE_SUCCESS;
  }

  public static int getSpawn(CommandSourceStack source) {
    var groups = WorldsCommands.manager(source).getGroups();
    var group = McWorld.groupOf(source.getLevel());

    source.sendSuccess(() -> Component.translatable(KEY + "spawn.query", id(group), spawn(groups.spawnOf(group))),
        false);
    return Command.SINGLE_SUCCESS;
  }

  public static int setSpawn(CommandSourceStack source, @Nullable Vec3 position) throws CommandSyntaxException {
    var level = source.getLevel();
    var group = McWorld.groupOf(level);
    if (WorldGroups.isDefault(group)) {
      throw WorldsCommands.failure(Component.translatable(KEY + "spawn.exception.default"));
    }
    var target = require(source, group);
    var spawn = SettingsCommand.respawnDataAt(source, position, level.dimension());
    WorldsCommands.call(() -> target.setSpawn(spawn));

    source.sendSuccess(() -> Component.translatable(KEY + "spawn.success", id(group), spawn(target.spawn())), false);
    return Command.SINGLE_SUCCESS;
  }

  public static int list(CommandSourceStack source) {
    var groups = WorldsCommands.manager(source).getGroups();

    var entries = new ArrayList<Component>();
    entries.add(entry(groups, McWorld.DEFAULT_GROUP));
    for (var group : new TreeSet<>(groups.all().keySet())) {
      entries.add(entry(groups, group));
    }

    source.sendSuccess(() -> Component.translatable(KEY + "list.success", entries.size(),
        ComponentUtils.formatList(entries, ComponentUtils.DEFAULT_SEPARATOR)), false);
    return entries.size();
  }

  private static Component entry(WorldGroups groups, Identifier group) {
    var isDefault = McWorld.DEFAULT_GROUP.equals(group);
    var command = "/worlds group join " + group;
    var members = groups.members(group);

    var lines = new ArrayList<Component>();
    lines.add(Component.literal(group.toString()).withStyle(ChatFormatting.BOLD));
    lines.add(CommonComponents.EMPTY);
    lines.addAll(describe(groups, group));
    if (!isDefault) {
      lines.add(CommonComponents.EMPTY);
      lines.add(Component.translatable("dev.wroud.mc.worlds.command.list.click", command)
          .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    return ComponentUtils.wrapInSquareBrackets(Component.literal(group.toString()))
        .withStyle(style -> style
            .withColor(members.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN)
            .withInsertion(group.toString())
            .withHoverEvent(new HoverEvent.ShowText(ComponentUtils.formatList(lines, CommonComponents.NEW_LINE)))
            .withClickEvent(isDefault ? null : new ClickEvent.SuggestCommand(command)));
  }

  private static List<Component> describe(WorldGroups groups, Identifier groupId) {
    var group = groups.get(groupId);
    var members = groups.members(groupId);

    var mains = new ArrayList<Component>();
    if (group != null) {
      for (var kind : WorldKind.values()) {
        var main = group.main(kind);
        if (main != null) {
          mains.add(Component.translatable(KEY + "main." + kind.key(), id(main)));
        }
      }
    }

    var lines = new ArrayList<Component>();
    lines.add(field("mains", mains.isEmpty() ? none() : ComponentUtils.formatList(mains, ComponentUtils.DEFAULT_SEPARATOR)));
    lines.add(field("members", members.isEmpty() ? none() : ComponentUtils.formatList(members, GroupCommand::id)));
    lines.add(field("spawn", spawn(groups.spawnOf(groupId))));
    return lines;
  }

  private static WorldGroup require(CommandSourceStack source, Identifier group) throws CommandSyntaxException {
    return WorldsCommands.call(() -> WorldsCommands.manager(source).getGroups().require(group));
  }

  private static Component spawn(WorldGroup.Spawn spawn) {
    var pos = spawn.spawn().pos();
    return Component.translatable(KEY + "spawn.value",
        Component.literal(String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())).withStyle(ChatFormatting.GOLD),
        id(spawn.spawn().dimension().identifier()),
        Component.translatable(KEY + "spawn.source." + spawn.source().key()));
  }

  private static Component field(String name, Component value) {
    return Component.translatable(KEY + name, value);
  }

  private static Component none() {
    return Component.translatable(KEY + "none").withStyle(ChatFormatting.GRAY);
  }

  private static Component id(Identifier id) {
    return Component.literal(id.toString()).withStyle(ChatFormatting.GOLD);
  }
}
