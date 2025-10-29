package dev.wroud.mc.worlds.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import dev.wroud.mc.worlds.McWorldMod;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.phys.Vec3;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SettingsCommand {
  private static final SimpleCommandExceptionType UNKNOWN_WORLD_EXCEPTION = new SimpleCommandExceptionType(
      Component.translatable("dev.wroud.mc.worlds.command.exception.unknown_world"));

  public static LiteralArgumentBuilder<CommandSourceStack> build() {
    return literal("settings")
        .requires(Permissions.require("dev.wroud.mc.worlds.command.settings", 2))
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
    var id = source.getLevel().dimension().location();
    
    var worlds = McWorldMod.getMcWorld(source.getServer()).orElseThrow();
    var levelData = worlds.getManager().getWorldsData().getLevelData(id);

    if (levelData == null) {
      throw UNKNOWN_WORLD_EXCEPTION.create();
    }

    boolean loadOnStartup = !levelData.isLazy();

    source.sendSuccess(
        () -> Component.translatable("dev.wroud.mc.worlds.command.settings.loadOnStartup.query", id.toString())
            .append(Component.literal(" "))
            .append(Component.literal(String.valueOf(loadOnStartup)).withStyle(ChatFormatting.GOLD)),
        false);

    return Command.SINGLE_SUCCESS;
  }

  public static int setLoadOnStartup(CommandSourceStack source, boolean loadOnStartup)
      throws CommandSyntaxException {
    var id = source.getLevel().dimension().location();
    
    var worlds = McWorldMod.getMcWorld(source.getServer()).orElseThrow();
    var levelData = worlds.getManager().getWorldsData().getLevelData(id);

    if (levelData == null) {
      throw UNKNOWN_WORLD_EXCEPTION.create();
    }

    levelData.setLazy(!loadOnStartup);

    source.sendSuccess(
        () -> Component.translatable("dev.wroud.mc.worlds.command.settings.loadOnStartup.success", id.toString())
            .append(Component.literal(" "))
            .append(Component.literal(String.valueOf(loadOnStartup)).withStyle(ChatFormatting.GOLD)),
        false);

    return Command.SINGLE_SUCCESS;
  }

  public static int getSpawn(CommandSourceStack source)
      throws CommandSyntaxException {
    var id = source.getLevel().dimension().location();
    
    var worlds = McWorldMod.getMcWorld(source.getServer()).orElseThrow();
    var levelData = worlds.getManager().getWorldsData().getLevelData(id);

    if (levelData == null) {
      throw UNKNOWN_WORLD_EXCEPTION.create();
    }

    var spawn = levelData.getRespawnData();
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

  public static int setSpawn(CommandSourceStack source, Vec3 position)
      throws CommandSyntaxException {
    var id = source.getLevel().dimension().location();
    
    var worlds = McWorldMod.getMcWorld(source.getServer()).orElseThrow();
    var levelData = worlds.getManager().getWorldsData().getLevelData(id);

    if (levelData == null) {
      throw UNKNOWN_WORLD_EXCEPTION.create();
    }

    var resourceKey = ResourceKey.create(Registries.DIMENSION, id);
    Vec3 spawnPos;
    float yaw;
    float pitch;

    if (position != null) {
      spawnPos = position;
      yaw = 0.0F;
      pitch = 0.0F;
    } else {
      spawnPos = source.getPosition();
      var rotation = source.getRotation();
      yaw = rotation.y;
      pitch = rotation.x;
    }

    var respawnData = RespawnData.of(resourceKey, BlockPos.containing(spawnPos), yaw, pitch);
    levelData.setSpawn(respawnData);

    source.sendSuccess(
        () -> Component.translatable("dev.wroud.mc.worlds.command.settings.spawn.success", id.toString())
            .append(Component.literal(" "))
            .append(Component.literal(String.format("(%.2f, %.2f, %.2f)", spawnPos.x, spawnPos.y, spawnPos.z))
                .withStyle(ChatFormatting.GOLD)),
        false);

    return Command.SINGLE_SUCCESS;
  }

}
