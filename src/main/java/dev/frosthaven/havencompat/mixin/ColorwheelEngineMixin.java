package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.colorwheel.ColorwheelCut;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each Colorwheel render pass: the ship planes go up before its draws and the clip state comes off after; see {@link ColorwheelCut}. */
@Pseudo
@Mixin(targets = ColorwheelCut.ENGINE, remap = false)
public abstract class ColorwheelEngineMixin {
    @Shadow private BlockPos renderOrigin;

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Ldev/djefrey/colorwheel/engine/ClrwlEngine;prepareFrame(Ldev/engine_room/flywheel/api/backend/RenderContext;)V"))
    private void haven_compat$beginCut(CallbackInfo ci) {
        ColorwheelCut.beginPass(this, renderOrigin);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void haven_compat$endCut(CallbackInfo ci) {
        ColorwheelCut.endPass();
    }
}
