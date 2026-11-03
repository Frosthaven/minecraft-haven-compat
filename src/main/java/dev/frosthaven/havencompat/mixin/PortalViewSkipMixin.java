package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Immersive Portal Rendering Disabled: Immersive Portals renders no portal view; see {@link PortalView}. */
@Mixin(targets = "qouteall.imm_ptl.core.render.renderer.PortalRenderer", remap = false)
public abstract class PortalViewSkipMixin {
    @Inject(method = "shouldSkipRenderingPortal", at = @At("HEAD"), cancellable = true)
    private static void haven_compat$skip(CallbackInfoReturnable<Boolean> cir) {
        if (PortalView.disabled()) cir.setReturnValue(true);
    }
}
