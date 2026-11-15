package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.djefrey.colorwheel.compile.ClrwlProgram;
import dev.frosthaven.havencompat.colorwheel.ColorwheelUnits;
import java.util.Set;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Colorwheel's texture units a program does not read go to the shader pack (macOS has 16 in all); see {@link ColorwheelUnits}. */
@Mixin(value = ClrwlProgram.class, remap = false)
public abstract class ColorwheelProgramMixin {
    @Unique private boolean haven_compat$crumbles;

    @WrapOperation(method = "postLink", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/program/ProgramSamplers;builder(ILjava/util/Set;)Lnet/irisshaders/iris/gl/program/ProgramSamplers$Builder;"))
    private ProgramSamplers.Builder haven_compat$usedUnits(int program, Set<Integer> reserved, Operation<ProgramSamplers.Builder> original) {
        Set<Integer> kept = ColorwheelUnits.reserved(program, reserved);
        haven_compat$crumbles = ColorwheelUnits.crumbles();
        return original.call(program, kept);
    }

    /** Iris refuses an external sampler on a unit that is not reserved. */
    @WrapWithCondition(method = "postLink", at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/program/ProgramSamplers$Builder;addExternalSampler(I[Ljava/lang/String;)V"))
    private boolean haven_compat$usedUnit(ProgramSamplers.Builder builder, int unit, String[] names) {
        return ColorwheelUnits.kept(unit);
    }

    @Inject(method = "bind", at = @At("RETURN"))
    private void haven_compat$ownSampler(CallbackInfo ci) {
        if (haven_compat$crumbles) ColorwheelUnits.bound();
    }
}
