package dev.wroud.mc.worlds.server.level;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public class LayeredGameRules extends GameRules {
  private final ServerLevel level;
  private final GameRules parent;
  private final GameRuleMap overrides;
  private final Set<GameRule<?>> available;

  public LayeredGameRules(ServerLevel level, GameRules parent, GameRuleMap overrides) {
    super(List.of());
    this.level = level;
    this.parent = parent;
    this.overrides = overrides;
    this.available = parent.availableRules().collect(Collectors.toSet());
    this.prune();
  }

  public void prune() {
    for (GameRule<?> rule : List.copyOf(this.overrides.keySet())) {
      if (!this.available.contains(rule) || PerWorldGameRules.isGlobal(rule)) {
        this.overrides.remove(rule);
      }
    }
  }

  public GameRules parent() {
    return this.parent;
  }

  public boolean isOverridden(GameRule<?> rule) {
    return this.overrides.has(rule);
  }

  public Set<GameRule<?>> overriddenRules() {
    return this.overrides.keySet();
  }

  public <T> boolean inherit(GameRule<T> rule) {
    if (!this.overrides.has(rule)) {
      return false;
    }

    this.overrides.remove(rule);
    PerWorldGameRules.onWorldGameRuleChanged(this.level, rule, this.parent.get(rule));
    return true;
  }

  @Override
  public Stream<GameRule<?>> availableRules() {
    return this.parent.availableRules();
  }

  @Override
  public <T> T get(GameRule<T> rule) {
    T value = this.overrides.get(rule);
    return value != null ? value : this.parent.get(rule);
  }

  @Override
  public <T> void set(GameRule<T> rule, T value, @Nullable MinecraftServer server) {
    if (PerWorldGameRules.isGlobal(rule)) {
      this.parent.set(rule, value, server);
    } else if (!this.available.contains(rule)) {
      super.set(rule, value, server);
    } else {
      this.overrides.set(rule, value);
      if (server != null) {
        PerWorldGameRules.onWorldGameRuleChanged(this.level, rule, value);
      }
    }
  }

  @Override
  public GameRules copy(FeatureFlagSet enabledFeatures) {
    GameRules copy = this.parent.copy(enabledFeatures);
    copy.setAll(this.overrides, null);
    return copy;
  }

  @Override
  public void setAll(GameRules other, @Nullable MinecraftServer server) {
    other.availableRules().forEach(rule -> this.setFrom(other, rule, server));
  }

  @Override
  public void visitGameRuleTypes(GameRuleTypeVisitor visitor) {
    this.parent.visitGameRuleTypes(visitor);
  }

  private <T> void setFrom(GameRules other, GameRule<T> rule, @Nullable MinecraftServer server) {
    this.set(rule, other.get(rule), server);
  }
}
