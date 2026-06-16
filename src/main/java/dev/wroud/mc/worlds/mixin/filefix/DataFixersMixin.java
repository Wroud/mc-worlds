package dev.wroud.mc.worlds.mixin.filefix;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;

import dev.wroud.mc.worlds.util.filefix.fixes.McWorldsDataFileFix;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import net.minecraft.util.filefix.FileFixerUpper;

/**
 * Mixin to inject the McWorldsDataFileFix into the DataFixers system.
 * 
 * This adds our file fix to migrate mc-worlds.dat to mc-worlds/worlds_data.dat
 * at the end of the addFixers method, using an existing schema version.
 */
@Mixin(DataFixers.class)
public class DataFixersMixin {
    
    @Inject(method = "addFixers", at = @At("TAIL"))
    private static void mcworlds$addFileFixers(DataFixerBuilder fixerUpper, FileFixerUpper.Builder fileFixerUpper, CallbackInfo ci) {
        int version = fileFixerUpper.fileFixes.getLast().getVersion() + 1;
        Schema schema = fileFixerUpper.addSchema(fixerUpper, version, NamespacedSchema::new);
        fileFixerUpper.addFixer(new McWorldsDataFileFix(schema));
    }
}
