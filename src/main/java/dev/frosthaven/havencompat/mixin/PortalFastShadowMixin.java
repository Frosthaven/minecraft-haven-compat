package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havencompat.portals.PortalView;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Immersive Portal Rendering Fast: the shader pack's shadow pass for a sunless world behind a portal (the Nether) draws
 * nothing (terrain, entities, block entities). The pass itself still runs (the clear, the pack's shadow composites), so
 * the pack's programs find the state they expect. In Shane's world the shadow pass was 17 % of the render thread's time
 * for a portal view into the Nether (2026-11-18), with its GPU time on top. Not for a world with a sun: an empty shadow
 * map lit an underground room like open ground (tested the same day).
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class PortalFastShadowMixin {
    private static final String OWNER = "Lnet/irisshaders/iris/shadows/ShadowRenderer;";

    @ModifyExpressionValue(method = "renderShadows", at = {
            @At(value = "FIELD", target = OWNER + "shouldRenderTerrain:Z", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = OWNER + "shouldRenderTranslucent:Z", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = OWNER + "shouldRenderEntities:Z", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = OWNER + "shouldRenderPlayer:Z", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = OWNER + "shouldRenderBlockEntities:Z", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = OWNER + "shouldRenderLightBlockEntities:Z", opcode = Opcodes.GETFIELD)})
    private boolean haven_compat$nothingInPortalViews(boolean draw) {
        return draw && !PortalView.fastSunlessNow();
    }
}
