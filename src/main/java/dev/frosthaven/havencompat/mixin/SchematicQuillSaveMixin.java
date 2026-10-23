package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.schematics.client.SchematicAndQuillHandler;
import dev.frosthaven.havencompat.schematics.ServerFillClient;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Schematic and Quill save first asks the server for the selection's block settings; see {@link ServerFillClient}. */
@Mixin(value = SchematicAndQuillHandler.class, remap = false)
public abstract class SchematicQuillSaveMixin {
    @Shadow public BlockPos firstPos;
    @Shadow public BlockPos secondPos;

    @Inject(method = "saveSchematic", at = @At("HEAD"), cancellable = true)
    private void haven$askServer(String name, boolean convertImmediately, CallbackInfo ci) {
        if (ServerFillClient.ask(name, convertImmediately, firstPos, secondPos)) {
            ci.cancel();
        }
    }
}
