package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.djefrey.colorwheel.compile.ClrwlProgramSources;
import dev.djefrey.colorwheel.compile.ClrwlPrograms;
import dev.djefrey.colorwheel.shaderpack.ClrwlProgramId;
import dev.frosthaven.havencompat.colorwheel.ColorwheelWarm;
import org.spongepowered.asm.mixin.Mixin;

/** Colorwheel's rewritten shader sources are shared by all pipelines of a dimension; see {@link ColorwheelWarm}. */
@Mixin(value = ClrwlProgramSources.class, remap = false)
public abstract class ColorwheelSourcesMixin {
    @WrapMethod(method = "getGbuffersSources")
    private ClrwlProgramSources.PatchedGbuffersSources haven_compat$shared(ClrwlProgramId id, ClrwlPrograms.OitMode oit, int ssboOffset,
            Operation<ClrwlProgramSources.PatchedGbuffersSources> original) {
        return ColorwheelWarm.cached(this, id, oit, ssboOffset, () -> original.call(id, oit, ssboOffset));
    }
}
