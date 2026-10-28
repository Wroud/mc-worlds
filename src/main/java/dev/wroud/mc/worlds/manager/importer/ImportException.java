package dev.wroud.mc.worlds.manager.importer;

import java.util.Arrays;
import java.util.Objects;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class ImportException extends Exception {
  public static final String KEY_PREFIX = "dev.wroud.mc.worlds.command.import.";
  private static final String MOD_KEY_PREFIX = "dev.wroud.mc.worlds.";

  private final String key;
  private final Object[] args;

  public ImportException(String key, Object... args) {
    super(key);
    this.key = key;
    this.args = args;
  }

  public ImportException(Throwable cause, String key, Object... args) {
    super(key, cause);
    this.key = key;
    this.args = args;
  }

  public static ImportException stopped(Object folder) {
    return new ImportException("failed", folder, Component.translatable(KEY_PREFIX + "reason.stopped"));
  }

  public static ImportException failed(Object folder, Throwable cause) {
    return new ImportException(cause, "failed", folder, Objects.requireNonNullElse(cause.getMessage(), cause.toString()));
  }

  public String translationKey() {
    return key.startsWith(MOD_KEY_PREFIX) ? key : KEY_PREFIX + key;
  }

  public Component toComponent() {
    return Component.translatable(translationKey(), Arrays.stream(args).map(ImportException::toArgument).toArray());
  }

  @Override
  public String getMessage() {
    return toComponent().getString();
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
