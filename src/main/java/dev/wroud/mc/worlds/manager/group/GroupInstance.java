package dev.wroud.mc.worlds.manager.group;

import com.mojang.serialization.DynamicOps;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

public abstract class GroupInstance {
  protected final MinecraftServer server;
  protected final WorldGroup group;

  protected GroupInstance(WorldGroup group) {
    this.server = group.server();
    this.group = group;
  }

  public final WorldGroup group() {
    return this.group;
  }

  public final void setDirty() {
    this.group.markDirty();
  }

  public <T> T save(DynamicOps<T> ops) {
    return ops.emptyMap();
  }

  public void tick() {
  }

  public boolean canEnter(Entity entity) {
    return true;
  }

  public void removed() {
  }
}
