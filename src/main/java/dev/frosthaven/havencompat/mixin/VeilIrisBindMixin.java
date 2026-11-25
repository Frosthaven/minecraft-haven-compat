package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.Log;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A fixed black box over the middle of the screen with Complementary Unbound (a player on an RTX 5080, 2026-11-25; gone
 * with Real-Time Shadows off, or with Reimagined). Veil 4.3.2 (inside Sable) binds Iris's colortex0 for every Veil
 * shader it applies through {@code veil$bindSimpleFramebuffer}, which its own mixin adds to Iris's pipeline: during the
 * shadow pass too, and in GUIs, so a Veil draw meant for the shadow map lands on the screen. Veil's open fix (FoundryMC/Veil
 * pull 213) binds only when Iris overrides shaders and the shadow pass is not running, and then through Iris's own
 * {@code bindDefault()}, which picks the colortex0 copy a pack that flips it (Complementary) draws to. The same here:
 * the bind is replaced, and {@link VeilIrisClearMixin} puts the previous framebuffer back when the shader is cleared.
 * Priority over Veil's 1000, so Veil's method is already merged when this applies.
 */
@Mixin(value = IrisRenderingPipeline.class, priority = 1500, remap = false)
public abstract class VeilIrisBindMixin {
    @Unique
    private static final Log haven_compat$LOG = new Log("[veil shadows] ");

    @Inject(method = "veil$bindSimpleFramebuffer", at = @At("HEAD"), cancellable = true, require = 0)
    private void haven_compat$bind(CallbackInfo ci) {
        IrisRenderingPipeline pipeline = (IrisRenderingPipeline) (Object) this;
        if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered() && pipeline.shouldOverrideShaders()) pipeline.bindDefault();
        haven_compat$LOG.once("on", "Veil shaders draw to Iris's own framebuffer only outside the shadow pass (Veil pull 213)");
        ci.cancel();
    }
}
