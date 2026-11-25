package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalView;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Immersive Portal Rendering Disabled: Immersive Portals renders no portal view. Performance mode: a portal that Immersive
 * Portals would draw inside another portal's view, or in the player's own view farther away than the set distance, gets
 * no view (the decision is taken after its own tests, so a portal it skips anyway, like the back of the portal being
 * looked through, is not counted). See {@link PortalView}.
 */
@Mixin(targets = "qouteall.imm_ptl.core.render.renderer.PortalRenderer", remap = false)
public abstract class PortalViewSkipMixin {
    @Inject(method = "shouldSkipRenderingPortal", at = @At("HEAD"), cancellable = true)
    private static void haven_compat$skip(CallbackInfoReturnable<Boolean> cir) {
        if (PortalView.disabled()) cir.setReturnValue(true);
    }

    @Inject(method = "shouldSkipRenderingPortal", at = @At("RETURN"), cancellable = true)
    private static void haven_compat$skipNested(@Coerce Entity portal, Supplier<?> frustum, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (PortalView.nestedNow()) {
            PortalView.lostView(portal);
            cir.setReturnValue(true);
        } else if (PortalView.farNow(portal)) {
            cir.setReturnValue(true);
        }
    }
}
