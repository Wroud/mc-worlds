package dev.wroud.mc.worlds.manager;

import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.resources.Identifier;

public final class TemporaryIds {
  public static final String NAMESPACE = "mc-worlds-temp";

  private static final int SUFFIX_LENGTH = 37;
  private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,64}");

  private TemporaryIds() {
  }

  public static boolean isTemporary(Identifier id) {
    return NAMESPACE.equals(id.getNamespace());
  }

  private static boolean isValidName(String name) {
    return NAME.matcher(name).matches();
  }

  public static String nameOf(Identifier id) {
    var path = id.getPath();
    return path.length() > SUFFIX_LENGTH ? path.substring(0, path.length() - SUFFIX_LENGTH) : path;
  }

  public static Identifier generate(String name) {
    if (!isValidName(name)) {
      throw new WorldsException("dev.wroud.mc.worlds.command.exception.invalid_temporary_name", name);
    }
    return Identifier.fromNamespaceAndPath(NAMESPACE, name + "-" + UUID.randomUUID());
  }
}
