package dev.wroud.mc.worlds.mixin.fixes;

import java.util.Optional;

import dev.wroud.mc.worlds.server.level.CustomServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootContext.Builder.class)
public abstract class LootContextBuilderMixin {

    @Shadow
    private @Nullable RandomSource random;

    @Shadow
    public abstract ServerLevel getLevel();

    @Inject(method = "create", at = @At("HEAD"))
    private void mcworlds$useWorldSequence(Optional<Identifier> randomSequenceKey,
            CallbackInfoReturnable<LootContext> cir) {
        if (this.random == null && randomSequenceKey.isPresent()
                && this.getLevel() instanceof CustomServerLevel level) {
            this.random = level.getRandomSequence(randomSequenceKey.get());
        }
    }
}
