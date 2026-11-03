package dev.wroud.mc.worlds.manager;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

public class WorldsException extends RuntimeException {
  private final transient Component component;

  public WorldsException(String key, Object... args) {
    this(null, Component.translatableEscape(key, args));
  }

  protected WorldsException(@Nullable Throwable cause, Component component) {
    super(cause);
    this.component = component;
  }

  public Component toComponent() {
    return this.component;
  }

  @Override
  public String getMessage() {
    return this.component.getString();
  }
}
