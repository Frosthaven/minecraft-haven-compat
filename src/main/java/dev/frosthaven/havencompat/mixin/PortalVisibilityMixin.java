package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havencompat.portals.PortalVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Immersive Portals' visibility test never waits for this frame's query; see {@link PortalVisibility}. */
@Mixin(targets = "qouteall.imm_ptl.core.portal.PortalRenderInfo", remap = false)
public abstract class PortalVisibilityMixin {
    @Inject(method = "isFrequentlyMispredicted", at = @At("HEAD"), cancellable = true)
    private void haven_compat$predict(CallbackInfoReturnable<Boolean> cir) {
        if (PortalVisibility.enabled()) cir.setReturnValue(false);
    }

    /** {@code queryStallCounter <= 3} is the other half of "no predict": the first four tests of a frame. */
    @ModifyExpressionValue(method = "renderAndDecideVisibility", at = @At(value = "FIELD",
            target = "Lqouteall/imm_ptl/core/render/QueryManager;queryStallCounter:I", opcode = org.objectweb.asm.Opcodes.GETSTATIC, ordinal = 0))
    private static int haven_compat$noStallBudget(int counter) {
        return PortalVisibility.enabled() ? Integer.MAX_VALUE : counter;
    }

    @Inject(method = "renderAndDecideVisibility", at = @At("RETURN"))
    private static void haven_compat$decided(CallbackInfoReturnable<Boolean> cir) {
        PortalVisibility.end(cir.getReturnValueZ());
    }
}
