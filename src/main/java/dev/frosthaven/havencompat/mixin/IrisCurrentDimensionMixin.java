package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PipelinePrewarm;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the game sets a client level, Iris's "last dimension" is the very object its "current dimension" returns, so its
 * handler does not destroy every pipeline; see {@link PipelinePrewarm#keeping}.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public abstract class IrisCurrentDimensionMixin {
    @Inject(method = "getCurrentDimension", at = @At("RETURN"))
    private static void haven_compat$sameAsLast(CallbackInfoReturnable<NamespacedId> cir) {
        if (PipelinePrewarm.keeping()) Iris.lastDimension = cir.getReturnValue();
    }
}
