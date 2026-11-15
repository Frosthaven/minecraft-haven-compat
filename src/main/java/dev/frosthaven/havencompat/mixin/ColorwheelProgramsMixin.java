package dev.frosthaven.havencompat.mixin;

import dev.djefrey.colorwheel.compile.core.ClrwlCompilationHarness;
import dev.frosthaven.havencompat.colorwheel.ColorwheelWarm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each program Colorwheel builds is recorded, so the next join can build it ahead; see {@link ColorwheelWarm#compiled}. */
@Mixin(value = ClrwlCompilationHarness.class, remap = false)
public abstract class ColorwheelProgramsMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void haven_compat$record(Object key, CallbackInfoReturnable<?> cir) {
        ColorwheelWarm.compiled(key);
    }
}
