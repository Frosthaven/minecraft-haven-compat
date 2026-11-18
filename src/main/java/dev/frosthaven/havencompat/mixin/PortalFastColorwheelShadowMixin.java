package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Immersive Portal Rendering Fast: Colorwheel draws no Create parts into a portal view's shadow map; see {@link PortalFastShadowMixin}. */
@Pseudo
@Mixin(targets = "dev.djefrey.colorwheel.ClrwlSafeFlwImpl", remap = false)
public abstract class PortalFastColorwheelShadowMixin {
    @Inject(method = "submitShadowRenderContext", at = @At("HEAD"), cancellable = true)
    private void haven_compat$noPortalShadow(CallbackInfo ci) {
        if (PortalView.fastSunlessNow()) ci.cancel();
    }
}
