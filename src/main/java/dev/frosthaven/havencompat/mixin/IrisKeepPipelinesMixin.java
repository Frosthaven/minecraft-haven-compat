package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PipelinePrewarm;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the game sets a client level, Iris's "destroy every pipeline" is refused; see {@link PipelinePrewarm#keeping}. */
@Mixin(Minecraft.class)
public abstract class IrisKeepPipelinesMixin {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void haven_compat$joining(CallbackInfo ci) {
        PipelinePrewarm.joining(true);
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void haven_compat$joined(CallbackInfo ci) {
        PipelinePrewarm.joining(false);
    }
}
