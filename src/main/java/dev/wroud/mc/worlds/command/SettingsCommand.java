package dev.wroud.mc.worlds.command;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.phys.Vec3;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SettingsCommand {
  public static LiteralArgumentBuilder<CommandSourceStack> build() {
    return literal("settings")
        .then(literal("loadOnStartup")
            .executes(context -> getLoadOnStartup(context.getSource()))
            .then(
                argument("value", BoolArgumentType.bool())
                    .executes(context -> setLoadOnStartup(
                        context.getSource(),
                        BoolArgumentType.getBool(context, "value")))))
        .then(literal("spawn")
            .executes(context -> getSpawn(context.getSource()))
            .then(literal("here")
                .executes(context -> setSpawn(
                    context.getSource(),
                    null)))
            .then(
                argument("position", Vec3Argument.vec3())
                    .executes(context -> setSpawn(
                        context.getSource(),
                        Vec3Argument.getVec3(context, "position")))));
  }

  public static int getLoadOnStartup(CommandSourceStack source)
      throws CommandSyntaxException {
    var id = source.getLevel().dimension().identifier();
    
    boolean loadOnStartup = WorldsCommands.call(() -> WorldsCommands.worlds(source).loadsOnStartup(id));

    source.sendSuccess(
        () -> Component.translatable("dev.wroud.mc.worlds.command.settings.loadOnStartup.query", id.toString())
            .append(Component.literal(" "))
            .append(Component.literal(String.valueOf(loadOnStartup)).withStyle(ChatFormatting.GOLD)),
        false);

    return Command.SINGLE_SUCCESS;
  }

  public static int setLoadOnStartup(CommandSourceStack source, boolean loadOnStartup)
      throws CommandSyntaxException {
    var id = source.getLevel().dimension().identifier();
    
    WorldsCommands.call(() -> WorldsCommands.worlds(source).setLoadOnStartup(id, loadOnStartup));

    source.sendSuccess(
        () -> Component.translatable("dev.wroud.mc.worlds.command.settings.loadOnStartup.success", id.toString())
            .append(Component.literal(" "))
            .append(Component.literal(String.valueOf(loadOnStartup)).withStyle(ChatFormatting.GOLD)),
        false);

    return Command.SINGLE_SUCCESS;
  }

  public static int getSpawn(CommandSourceStack source)
      throws CommandSyntaxException {
    var id = source.getLevel().dimension().identifier();
    
    var spawn = WorldsCommands.call(() -> WorldsCommands.worlds(source).worldSpawn(id));
    if (spawn == null || spawn == RespawnData.DEFAULT) {
      source.sendSuccess(
          () -> Component.translatable("dev.wroud.mc.worlds.command.settings.spawn.query.none", id.toString()),
          false);
    } else {
      var pos = spawn.pos();
      source.sendSuccess(
          () -> Component.translatable("dev.wroud.mc.worlds.command.settings.spawn.query", id.toString())
              .append(Component.literal(" "))
              .append(Component.literal(String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()))
                  .withStyle(ChatFormatting.GOLD))
              .append(Component.literal(" "))
              .append(Component.translatable("dev.wroud.mc.worlds.command.settings.spawn.query.rotation"))
              .append(Component.literal(" "))
              .append(Component.literal(String.format("(%.1f, %.1f)", spawn.yaw(), spawn.pitch()))
                  .withStyle(ChatFormatting.GOLD)),
          false);
    }

    return Command.SINGLE_SUCCESS;
  }

  public static int setSpawn(CommandSourceStack source, @Nullable Vec3 position)
      throws CommandSyntaxException {
    var id = source.getLevel().dimension().identifier();
    
    var respawnData = respawnDataAt(source, position, source.getLevel().dimension());
    WorldsCommands.call(() -> WorldsCommands.worlds(source).setWorldSpawn(id, respawnData));

    var spawnPos = position != null ? position : source.getPosition();
    source.sendSuccess(
        () -> Component.translatable("dev.wroud.mc.worlds.command.settings.spawn.success", id.toString())
            .append(Component.literal(" "))
            .append(Component.literal(String.format("(%.2f, %.2f, %.2f)", spawnPos.x, spawnPos.y, spawnPos.z))
                .withStyle(ChatFormatting.GOLD)),
        false);

    return Command.SINGLE_SUCCESS;
  }

  public static RespawnData respawnDataAt(CommandSourceStack source, @Nullable Vec3 position,
      ResourceKey<Level> dimension) {
    if (position != null) {
      return RespawnData.of(dimension, BlockPos.containing(position), 0.0F, 0.0F);
    }
    var rotation = source.getRotation();
    return RespawnData.of(dimension, BlockPos.containing(source.getPosition()), rotation.y, rotation.x);
  }

}
