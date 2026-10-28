package dev.wroud.mc.worlds.manager.importer;

import java.nio.file.Path;
import java.util.List;

import net.minecraft.resources.Identifier;

public record SourceDimension(Identifier id, List<Path> parts, boolean empty, boolean storedGenerator) {
}
