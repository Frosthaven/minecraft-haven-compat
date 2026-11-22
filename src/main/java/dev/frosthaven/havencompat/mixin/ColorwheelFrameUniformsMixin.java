package dev.frosthaven.havencompat.mixin;

import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.frosthaven.havencompat.colorwheel.ColorwheelFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Which view Colorwheel's shared frame data holds; see {@link ColorwheelFrame}. */
@Pseudo
@Mixin(targets = "dev.djefrey.colorwheel.engine.uniform.ClrwlFrameUniforms", remap = false)
public abstract class ColorwheelFrameUniformsMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private static void haven_compat$wroteFrame(RenderContext context, CallbackInfo ci) {
        ColorwheelFrame.wroteFrame(context);
    }
}
