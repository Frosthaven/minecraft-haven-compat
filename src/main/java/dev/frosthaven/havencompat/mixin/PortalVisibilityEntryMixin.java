package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the start of a visibility test and its query of this frame; see {@link PortalVisibility}. */
@Mixin(targets = "qouteall.imm_ptl.core.portal.PortalRenderInfo$Visibility", remap = false)
public abstract class PortalVisibilityEntryMixin {
    @Inject(method = "acquireThisFrameQuery", at = @At("RETURN"))
    private void haven_compat$begin(CallbackInfoReturnable<Object> cir) {
        if (PortalVisibility.enabled()) PortalVisibility.begin(this, cir.getReturnValue());
    }
}
