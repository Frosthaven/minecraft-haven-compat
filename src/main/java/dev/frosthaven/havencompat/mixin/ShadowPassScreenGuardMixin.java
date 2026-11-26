package dev.frosthaven.havencompat.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.frosthaven.havencompat.Log;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The rest of the Complementary Unbound black box (0.43.1; a player on an RTX 5080 still saw it with 0.43.0, now behind
 * the minimap; gone with Real-Time Shadows OFF). Veil's {@code AdvancedFbo.unbind()} is
 * {@code Minecraft.getMainRenderTarget().bindWrite(true)}: called in the middle of Iris's shadow pass (Veil's first
 * person renderer, its pipeline and perspective renderers, its framebuffer stack), it switches drawing to the screen
 * image with the screen's viewport, and the rest of that shadow pass lands on the screen as a solid shape placed by the
 * shadow camera. While {@code ShadowRenderer.ACTIVE}, a {@code bindWrite} on the main target is refused unless Iris
 * itself asks (it binds the main target at the end of its shadow pass before clearing the flag); each refused caller is
 * logged once.
 */
@Mixin(RenderTarget.class)
public abstract class ShadowPassScreenGuardMixin {
    @Unique
    private static final Log haven_compat$LOG = new Log("[shadow pass] ");
    @Unique
    private static final StackWalker haven_compat$WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    @Inject(method = "bindWrite", at = @At("HEAD"), cancellable = true)
    private void haven_compat$keepShadowTarget(boolean setViewport, CallbackInfo ci) {
        if (!ShadowRenderer.ACTIVE || (Object) this != Minecraft.getInstance().getMainRenderTarget()) return;
        String caller = haven_compat$WALKER.walk(frames -> frames
                .map(f -> f.getDeclaringClass().getName())
                .filter(n -> !n.startsWith("com.mojang.blaze3d.pipeline.") && !n.startsWith("dev.frosthaven.havencompat."))
                .findFirst().orElse("?"));
        if (caller.startsWith("net.irisshaders.")) return;
        haven_compat$LOG.warnOnce("refused-" + caller, "refused a switch to the screen image during Iris's shadow pass, asked by {}", caller);
        ci.cancel();
    }
}
