package dev.frosthaven.havencompat.mixin;

import dev.djefrey.colorwheel.engine.ShadowRenderContext;
import dev.djefrey.colorwheel.engine.TranslucentRenderContext;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.frosthaven.havencompat.colorwheel.ColorwheelCut;
import dev.frosthaven.havencompat.colorwheel.ColorwheelFrame;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Before each Colorwheel draw: the shared camera data is this view's again; see {@link ColorwheelFrame}. */
@Pseudo
@Mixin(targets = ColorwheelCut.ENGINE, remap = false)
public abstract class ColorwheelFrameMixin {
    @Shadow private void preparePass(RenderContext context, IrisRenderingPipeline irisPipeline) {}

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Ldev/djefrey/colorwheel/engine/ClrwlEngine;prepareFrame(Ldev/engine_room/flywheel/api/backend/RenderContext;)V"))
    private void haven_compat$ownFrame(RenderContext context, CallbackInfo ci) {
        if (context instanceof ShadowRenderContext) return;   // the shadow pass has its own buffer
        ColorwheelFrame.restoreFrame(context);
        // The solid pass writes the pass data itself; the translucent pass uses what the last solid pass wrote.
        if (context instanceof TranslucentRenderContext && ColorwheelFrame.passStale(context)
                && Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline) {
            preparePass(context, pipeline);
        }
    }
}
