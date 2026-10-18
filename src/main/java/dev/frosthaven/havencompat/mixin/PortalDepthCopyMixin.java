package dev.frosthaven.havencompat.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.frosthaven.havencompat.portals.PortalDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code IPIrisHelper}'s two copies call {@code GL43C.glCopyImageSubData} with no capability check, which aborts the
 * JVM where the function is missing (macOS). There they become blits; see {@link PortalDepth}.
 */
@Mixin(targets = PortalDepth.COPY_TARGET, remap = false)
public abstract class PortalDepthCopyMixin {
    @Inject(method = "newCopyDepthStencil", at = @At("HEAD"), cancellable = true)
    private static void haven_compat$blitDepthStencil(RenderTarget from, RenderTarget to, CallbackInfo ci) {
        if (PortalDepth.noCopyImage()) {
            PortalDepth.blitDepthStencil(from, to);
            ci.cancel();
        }
    }

    @Inject(method = "copyColor", at = @At("HEAD"), cancellable = true)
    private static void haven_compat$blitColor(RenderTarget from, RenderTarget to, CallbackInfo ci) {
        if (PortalDepth.noCopyImage()) {
            PortalDepth.blitColor(from, to);
            ci.cancel();
        }
    }
}
