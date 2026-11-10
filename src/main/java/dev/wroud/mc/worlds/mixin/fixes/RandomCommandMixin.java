package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.RandomCommand;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RandomCommand.class)
public class RandomCommandMixin {
    private static final String GET_RANDOM_SEQUENCES =
            "Lnet/minecraft/server/MinecraftServer;getRandomSequences()Lnet/minecraft/world/RandomSequences;";

    @WrapOperation(
            method = {"resetSequence", "resetAllSequences", "resetAllSequencesAndSetNewDefaults"},
            at = @At(value = "INVOKE", target = GET_RANDOM_SEQUENCES))
    private static RandomSequences mcworlds$worldSequences(MinecraftServer server, Operation<RandomSequences> original,
            @Local(argsOnly = true) CommandSourceStack source) {
        if (source.getLevel() instanceof CustomServerLevel level) {
            RandomSequences sequences = level.getRandomSequences();
            sequences.setDirty();
            return sequences;
        }
        return original.call(server);
    }

    @WrapOperation(
            method = "suggestRandomSequence",
            at = @At(value = "INVOKE", target = GET_RANDOM_SEQUENCES))
    private static RandomSequences mcworlds$suggestWorldSequences(MinecraftServer server,
            Operation<RandomSequences> original, @Local(argsOnly = true) CommandContext<CommandSourceStack> context) {
        return context.getSource().getLevel() instanceof CustomServerLevel level ? level.getRandomSequences()
                : original.call(server);
    }

    @WrapOperation(
            method = "randomSample",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getRandomSequence(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/util/RandomSource;"))
    private static RandomSource mcworlds$worldSequence(MinecraftServer server, Identifier key,
            Operation<RandomSource> original, @Local(argsOnly = true) CommandSourceStack source) {
        return source.getLevel() instanceof CustomServerLevel level ? level.getRandomSequence(key)
                : original.call(server, key);
    }
}
