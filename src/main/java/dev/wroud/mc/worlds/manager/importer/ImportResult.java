package dev.wroud.mc.worlds.manager.importer;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public record ImportResult(List<Identifier> worlds, List<Line> lines) {
  public record Line(Component message, boolean failure) {
  }
}
