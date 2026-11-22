package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalLookAround;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a near portal's view is drawn, one more terrain setup that looks another way, so the far side's meshes get built;
 * the next drawing sets up the real view again. See {@link PortalLookAround}.
 */
@Mixin(LevelRenderer.class)
public abstract class PortalLookAroundMixin {
    @Shadow private void setupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator) {}

    @Shadow public abstract void needsUpdate();

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void haven_compat$lookAround(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        Frustum look = PortalLookAround.next();
        if (look == null) return;
        needsUpdate();
        setupRender(camera, look, false, false);
        needsUpdate();   // the next drawing of this view computes its own visible set again
    }
}
