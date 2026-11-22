package dev.frosthaven.havencompat.mixin;

import dev.djefrey.colorwheel.engine.ClrwlDrawManager;
import dev.djefrey.colorwheel.engine.ClrwlEngine;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import dev.frosthaven.havencompat.colorwheel.ColorwheelCut;
import dev.frosthaven.havencompat.colorwheel.ColorwheelWarm;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A new engine takes the draw manager that was built at the title screen, programs and all; see {@link ColorwheelWarm#queueSpares}. */
@Pseudo
@Mixin(targets = ColorwheelCut.ENGINE, remap = false)
public abstract class ColorwheelSpareMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private ShaderPack pack;
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.irisshaders.iris.shaderpack.materialmap.NamespacedId dimension;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Ldev/djefrey/colorwheel/engine/ClrwlEngine$DrawManagerFactory;build(Ldev/engine_room/flywheel/backend/glsl/ShaderSources;Lnet/irisshaders/iris/shaderpack/ShaderPack;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Z)Ldev/djefrey/colorwheel/engine/ClrwlDrawManager;"))
    private ClrwlDrawManager<?> haven_compat$spare(ClrwlEngine.DrawManagerFactory factory, ShaderSources sources, ShaderPack pack, ProgramSet set, boolean fallback) {
        Object spare = ColorwheelWarm.spare(set);
        return spare != null ? (ClrwlDrawManager<?>) spare : factory.build(sources, pack, set, fallback);
    }

    /** A deleted engine's draw manager goes back to the spares; see {@link ColorwheelWarm#recycle}. */
    @Redirect(method = "delete", at = @At(value = "INVOKE", target = "Ldev/djefrey/colorwheel/engine/ClrwlDrawManager;delete()V"))
    private void haven_compat$recycle(ClrwlDrawManager<?> manager) {
        if (!ColorwheelWarm.recycle(pack, dimension, manager)) manager.delete();
    }
}
