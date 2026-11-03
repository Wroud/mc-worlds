package dev.wroud.mc.worlds.manager.importer;

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.wroud.mc.worlds.manager.WorldsException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class ImportException extends WorldsException {
  public static final String KEY_PREFIX = "dev.wroud.mc.worlds.command.import.";

  public ImportException(String key, Object... args) {
    this(null, key, args);
  }

  public ImportException(@Nullable Throwable cause, String key, Object... args) {
    super(cause, Component.translatable(KEY_PREFIX + key, Arrays.stream(args).map(ImportException::toArgument).toArray()));
  }

  public static ImportException stopped(Object folder) {
    return new ImportException("failed", folder, Component.translatable(KEY_PREFIX + "reason.stopped"));
  }

  public static ImportException failed(Object folder, Throwable cause) {
    return new ImportException(cause, "failed", folder, Objects.requireNonNullElse(cause.getMessage(), cause.toString()));
  }

  private static Object toArgument(Object arg) {
    if (arg instanceof Component || TranslatableContents.isAllowedPrimitiveArgument(arg)) {
      return arg;
    }
    if (arg instanceof Throwable throwable) {
      return String.valueOf(throwable.getMessage());
    }
    return String.valueOf(arg);
  }
}
