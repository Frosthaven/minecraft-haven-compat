package dev.frosthaven.havencompat.mixin;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.djefrey.colorwheel.compile.ClrwlProgram;
import dev.frosthaven.havencompat.colorwheel.ColorwheelUnits;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Colorwheel's unused transparency texture units go back to the shader pack (macOS has 16 in all); see {@link ColorwheelUnits}. */
@Mixin(value = ClrwlProgram.class, remap = false)
public abstract class ColorwheelProgramMixin {
    @ModifyReturnValue(method = "getReservedTextureUnits", at = @At("RETURN"))
    private static ImmutableSet<Integer> haven_compat$usedUnits(ImmutableSet<Integer> units) {
        return ColorwheelUnits.reserved(units);
    }

    /** Iris refuses an external sampler on a unit that is not reserved. */
    @WrapWithCondition(method = "postLink", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/program/ProgramSamplers$Builder;addExternalSampler(I[Ljava/lang/String;)V"))
    private boolean haven_compat$usedUnit(ProgramSamplers.Builder builder, int unit, String[] names) {
        return ColorwheelUnits.used(unit);
    }
}
