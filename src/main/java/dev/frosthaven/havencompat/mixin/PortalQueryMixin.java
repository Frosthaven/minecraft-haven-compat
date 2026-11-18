package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reads a visibility query only when the GPU has its answer ready; see {@link PortalVisibility}. */
@Mixin(targets = "qouteall.imm_ptl.core.render.GlQueryObject", remap = false)
public abstract class PortalQueryMixin {
    @Shadow private int idQueryObject;

    @Inject(method = "fetchQueryResult", at = @At("HEAD"), cancellable = true)
    private void haven_compat$noWait(CallbackInfoReturnable<Boolean> cir) {
        Boolean answer = PortalVisibility.answer(this, idQueryObject);
        if (answer != null) cir.setReturnValue(answer);
    }
}
