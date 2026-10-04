package dev.wroud.mc.worlds.mixin.compat;

import dev.wroud.mc.worlds.util.DimensionDetectionUtil;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Iris.class)
public class IrisMixin {
    @Inject(
        method = "getCurrentDimension",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/DimensionType;skybox()Lnet/minecraft/world/level/dimension/DimensionType$Skybox;"
        ),
        cancellable = true,
        require = 0
    )
    private static void useNetherProgramsForNetherLikeDimensions(CallbackInfoReturnable<NamespacedId> cir) {
        var level = Minecraft.getInstance().level;
        if (level != null && DimensionDetectionUtil.isNetherLikeDimension(level)) {
            cir.setReturnValue(DimensionId.NETHER);
        }
    }
}
