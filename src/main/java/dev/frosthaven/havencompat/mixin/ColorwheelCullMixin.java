package dev.frosthaven.havencompat.mixin;

import dev.djefrey.colorwheel.shaderpack.ClrwlProgramGroup;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.PackDefaults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Colorwheel 1.3.2's indirect backend culls Create's instanced parts on the graphics card (frustum, and occlusion
 * against a depth pyramid with a "visible last frame" bit per part). That state is per engine and assumes one camera a
 * frame. Immersive Portals draws a level from several cameras in one frame (the player's view, each portal view, a
 * carriage's far half), so parts in plain sight were culled: a train's carriage, and the tracks with it, vanished and
 * came back near portals and for seconds after a crossing, only with a shader pack on. Measured on the same train ride
 * (frames where the carriage floor under the rider changed, 2026-11-22): 127 to 160 with the culling, 19 with both kinds
 * off (shader pack properties {@code occlusion.culling} and {@code frustum.culling}), 19 with shaders off, 24 on
 * Colorwheel's instancing backend; the frame rate was the same with and without the culling (75 a second there).
 * <p>
 * So with Immersive Portals the main pass culls nothing on the graphics card: both answers read false, whatever the
 * shader pack says. The shadow pass keeps its own setting. On again: {@code "colorwheel": {"gpuCulling": true}} in
 * {@code config/haven-pack-defaults.json}.
 */
@Pseudo
@Mixin(targets = "dev.djefrey.colorwheel.shaderpack.ClrwlPackDirectives", remap = false)
public abstract class ColorwheelCullMixin {
    @Unique private static final Log HAVEN_COMPAT$LOG = new Log("[colorwheel cull] ");
    @Unique private static Boolean haven_compat$off;

    @Unique
    private static boolean haven_compat$off() {
        if (haven_compat$off == null) {
            boolean keep = false;
            try {
                var section = PackDefaults.section(PackDefaults.file(), "colorwheel");
                if (section != null && section.has("gpuCulling")) keep = section.get("gpuCulling").getAsBoolean();
            } catch (Exception e) {
                HAVEN_COMPAT$LOG.warnOnce("config", "could not read {} ({}); Colorwheel's culling stays off", PackDefaults.FILE, e.toString());
            }
            haven_compat$off = !keep;
            if (!keep) HAVEN_COMPAT$LOG.once("off", "Colorwheel's frustum and occlusion culling of the main pass are off: with Immersive Portals' several views a frame they hid parts in plain sight");
        }
        return haven_compat$off;
    }

    @Inject(method = "getOcclusionCulling", at = @At("HEAD"), cancellable = true)
    private void haven_compat$noOcclusion(ClrwlProgramGroup group, CallbackInfoReturnable<Boolean> cir) {
        if (group == ClrwlProgramGroup.GBUFFERS && haven_compat$off()) cir.setReturnValue(false);
    }

    @Inject(method = "getFrustumCulling", at = @At("HEAD"), cancellable = true)
    private void haven_compat$noFrustum(ClrwlProgramGroup group, CallbackInfoReturnable<Boolean> cir) {
        if (group == ClrwlProgramGroup.GBUFFERS && haven_compat$off()) cir.setReturnValue(false);
    }
}
