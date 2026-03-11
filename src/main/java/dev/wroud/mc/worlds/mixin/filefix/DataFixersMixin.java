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
        // Add a new schema version for our mod's file fix
        // This runs after all vanilla file fixes (the last one is at version 4773)
        Schema schema = fileFixerUpper.addSchema(fixerUpper, 4774, NamespacedSchema::new);
        fileFixerUpper.addFixer(new McWorldsDataFileFix(schema));
    }
}
