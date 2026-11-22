package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.colorwheel.ProgramBinaries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Colorwheel's linker loads a program from disk when it can, and links it when it must; see {@link ProgramBinaries}. */
@Pseudo
@Mixin(targets = "dev.djefrey.colorwheel.compile.core.ClrwlProgramLinker", remap = false)
public abstract class ColorwheelLinkerMixin {
    @Redirect(method = "linkInternal", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20;glLinkProgram(I)V"))
    private void haven_compat$cachedLink(int program) {
        ProgramBinaries.link(program);
    }
}
