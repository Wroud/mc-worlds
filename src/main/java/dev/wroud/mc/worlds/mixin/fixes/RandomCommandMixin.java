package dev.wroud.mc.worlds.mixin.fixes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.suggestions.ServerOnlyCustomSuggestion;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.RandomCommand;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

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

    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/suggestions/SuggestionProviders;serverOnly(Lnet/minecraft/commands/suggestions/ServerOnlyCustomSuggestion;)Lcom/mojang/brigadier/suggestion/SuggestionProvider;"))
    private static ServerOnlyCustomSuggestion mcworlds$suggestWorldSequences(ServerOnlyCustomSuggestion original) {
        return (server, context, builder) -> {
            if (context.getSource().level() instanceof CustomServerLevel level) {
                List<String> ids = new ArrayList<>();
                level.getRandomSequences().forAllSequences((key, sequence) -> ids.add(key.toString()));
                return SuggestionContext.suggest(ids, builder);
            }
            return original.generate(server, context, builder);
        };
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
