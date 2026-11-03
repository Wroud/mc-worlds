package dev.wroud.mc.worlds.manager;

import java.util.HashMap;
import java.util.Map;

import dev.wroud.mc.worlds.McWorldMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

public final class TemporaryStorage {
  private static final Identifier STORAGE = McWorldMod.id("temporary");
  private static final String WORLDS = "worlds";
  private static final String GROUPS = "groups";
  private static final String ID = "id";

  private final MinecraftServer server;
  private final Map<String, Identifier> latestWorlds = new HashMap<>();

  TemporaryStorage(MinecraftServer server) {
    this.server = server;
  }

  public void beginWorld(Identifier id) {
    if (!TemporaryIds.isTemporary(id)) {
      return;
    }
    var name = TemporaryIds.nameOf(id);
    this.latestWorlds.put(name, id);
    var root = this.server.getCommandStorage().get(STORAGE);
    var worlds = root.getCompoundOrEmpty(WORLDS);
    if (worlds.contains(name)) {
      worlds.remove(name);
      this.store(root, WORLDS, worlds);
    }
  }

  public void worldReady(Identifier id) {
    var name = TemporaryIds.nameOf(id);
    if (TemporaryIds.isTemporary(id) && id.equals(this.latestWorlds.get(name))) {
      this.put(WORLDS, name, id);
    }
  }

  public void putGroup(Identifier id) {
    this.put(GROUPS, TemporaryIds.nameOf(id), id);
  }

  public void forget(Identifier id) {
    if (!TemporaryIds.isTemporary(id)) {
      return;
    }
    var name = TemporaryIds.nameOf(id);
    this.latestWorlds.remove(name, id);
    var root = this.server.getCommandStorage().get(STORAGE);
    this.forget(root, WORLDS, name, id);
    this.forget(root, GROUPS, name, id);
  }

  public void clear() {
    this.latestWorlds.clear();
    var storage = this.server.getCommandStorage();
    if (!storage.get(STORAGE).isEmpty()) {
      storage.set(STORAGE, new CompoundTag());
    }
  }

  private void forget(CompoundTag root, String section, String name, Identifier id) {
    var entries = root.getCompoundOrEmpty(section);
    if (id.toString().equals(entries.getCompoundOrEmpty(name).getStringOr(ID, ""))) {
      entries.remove(name);
      this.store(root, section, entries);
    }
  }

  private void put(String section, String name, Identifier id) {
    var root = this.server.getCommandStorage().get(STORAGE);
    var entries = root.getCompoundOrEmpty(section);
    var entry = new CompoundTag();
    entry.putString(ID, id.toString());
    entries.put(name, entry);
    this.store(root, section, entries);
  }

  private void store(CompoundTag root, String section, CompoundTag entries) {
    if (entries.isEmpty()) {
      root.remove(section);
    } else {
      root.put(section, entries);
    }
    this.server.getCommandStorage().set(STORAGE, root);
  }
}
