package dev.wroud.mc.worlds.command;

import java.util.Comparator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import dev.wroud.mc.worlds.server.level.LayeredGameRules;
import dev.wroud.mc.worlds.server.level.PerWorldGameRules;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;

import static net.minecraft.commands.Commands.literal;

public class WorldGameRuleCommand {
  private static final String KEY = "dev.wroud.mc.worlds.command.gamerule.";

  private static final DynamicCommandExceptionType SERVER_RULES_EXCEPTION = new DynamicCommandExceptionType(
      world -> Component.translatableEscape(KEY + "inherit.server_rules", world));

  private static final Dynamic2CommandExceptionType NOT_OVERRIDDEN_EXCEPTION = new Dynamic2CommandExceptionType(
      (world, rule) -> Component.translatableEscape(KEY + "inherit.not_overridden", world, rule));

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
    LiteralArgumentBuilder<CommandSourceStack> base = literal("gamerule")
        .executes(c -> list(c.getSource()));
    new GameRules(context.enabledFeatures()).visitGameRuleTypes(new GameRuleTypeVisitor() {
      @Override
      public <T> void visit(GameRule<T> rule) {
        base.then(literal(rule.id()).then(inherit(rule)))
            .then(literal(rule.getIdentifier().toString()).then(inherit(rule)));
      }
    });
    dispatcher.register(base);
  }

  public static MutableComponent scoped(String action, CustomServerLevel level, GameRule<?> rule) {
    if (PerWorldGameRules.isGlobal(rule)) {
      return Component.translatable(KEY + action + ".server", rule.id(),
          level.getServer().getGameRules().getAsString(rule));
    }
    var rules = level.getGameRules();
    var origin = rules instanceof LayeredGameRules layered && !layered.isOverridden(rule) ? ".inherited" : ".world";
    return Component.translatable(KEY + action + origin, rule.id(), rules.getAsString(rule),
        level.dimension().identifier().toString());
  }

  private static LiteralArgumentBuilder<CommandSourceStack> inherit(GameRule<?> rule) {
    return literal("inherit")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .executes(c -> inherit(c.getSource(), rule));
  }

  private static int inherit(CommandSourceStack source, GameRule<?> rule) throws CommandSyntaxException {
    var world = source.getLevel().dimension().identifier().toString();
    if (!(source.getLevel().getGameRules() instanceof LayeredGameRules rules)) {
      throw SERVER_RULES_EXCEPTION.create(world);
    }
    if (!rules.inherit(rule)) {
      throw NOT_OVERRIDDEN_EXCEPTION.create(world, rule.id());
    }

    source.sendSuccess(() -> Component.translatable(KEY + "inherit.success", rule.id(), rules.getAsString(rule),
        world), true);
    return Command.SINGLE_SUCCESS;
  }

  private static int list(CommandSourceStack source) throws CommandSyntaxException {
    var world = source.getLevel().dimension().identifier().toString();
    if (!(source.getLevel().getGameRules() instanceof LayeredGameRules rules)) {
      throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
    }

    var overridden = rules.overriddenRules().stream().sorted(Comparator.comparing(GameRule::id)).toList();
    if (overridden.isEmpty()) {
      source.sendSuccess(() -> Component.translatable(KEY + "list.none", world), false);
      return 0;
    }

    source.sendSuccess(() -> Component.translatable(KEY + "list", world), false);
    for (GameRule<?> rule : overridden) {
      source.sendSuccess(() -> Component.translatable(KEY + "list.entry", rule.id(), rules.getAsString(rule),
          rules.parent().getAsString(rule)), false);
    }
    return overridden.size();
  }
}
