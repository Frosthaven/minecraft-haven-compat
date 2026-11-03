package dev.frosthaven.havencompat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havencompat.portals.PortalPlane;
import dev.frosthaven.havencompat.portals.PortalView;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Portal View Disabled: the portal entity draws a purple plane; see {@link PortalPlane}. */
@Mixin(targets = "qouteall.imm_ptl.core.render.PortalEntityRenderer", remap = false)
public abstract class PortalViewPlaneMixin {
    @Inject(method = "render(Lqouteall/imm_ptl/core/portal/Portal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void haven_compat$plane(@Coerce Entity portal, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (PortalView.disabled()) PortalPlane.draw(portal, pose, buffers);
    }
}
