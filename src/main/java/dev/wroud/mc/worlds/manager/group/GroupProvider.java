package dev.wroud.mc.worlds.manager.group;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Dynamic;


@FunctionalInterface
public interface GroupProvider {
  GroupInstance create(WorldGroup group, @Nullable Dynamic<?> saved);
}
