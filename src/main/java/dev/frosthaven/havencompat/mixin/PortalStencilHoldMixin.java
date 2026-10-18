package dev.frosthaven.havencompat.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.frosthaven.havencompat.portals.PortalDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Immersive Portals turns the main framebuffer's stencil off in its shader renderers
 * ({@code IPPortingLibCompat.setIsStencilEnabled(main, false)} in each {@code prepareRendering}). On a driver without
 * glCopyImageSubData that leaves the main depth buffer in a format the portal buffers do not have, and the depth blit
 * between them fails; see {@link PortalDepth}. The argument becomes true for the main framebuffer there.
 */
@Mixin(targets = PortalDepth.STENCIL_TARGET, remap = false)
public abstract class PortalStencilHoldMixin {
    @ModifyVariable(method = "setIsStencilEnabled", at = @At("HEAD"), argsOnly = true)
    private static boolean haven_compat$keepMainStencil(boolean enabled, RenderTarget target, boolean enabledArg) {
        return PortalDepth.keepMainStencil(target, enabled);
    }
}
