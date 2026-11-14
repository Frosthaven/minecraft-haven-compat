package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.colorwheel.ColorwheelCut;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Colorwheel's shader compile: its vertex shaders get Immersive Portals' two cut planes; see {@link ColorwheelCut}. */
@Pseudo
@Mixin(targets = ColorwheelCut.COMPILATION, remap = false)
public abstract class ColorwheelCompileMixin {
    @ModifyArg(method = "compile", index = 1, at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/backend/gl/GlCompat;safeShaderSource(ILjava/lang/CharSequence;)V"))
    private CharSequence haven_compat$cutPlanes(CharSequence source) {
        return ColorwheelCut.patch(source);
    }
}
