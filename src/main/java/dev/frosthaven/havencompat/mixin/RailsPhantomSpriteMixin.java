package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.railwayteam.railways.content.custom_tracks.phantom.PhantomSpriteManager;
import com.railwayteam.railways.mixin_interfaces.IPotentiallyInvisibleSpriteContents;
import dev.frosthaven.havencompat.Log;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Steam 'n' Rails 0.2.1 keeps weak references to every phantom track sprite it ever saw and uploads each again when the
 * phantom tracks' visibility changes. A sprite of an atlas that a resource reload replaced can still be alive then; its
 * upload against the new atlas reads mip levels it never had ("Index 1 out of bounds for length 1" in
 * SpriteContents.upload): the game crashed at start (traintest rig, 2026-11-25, twice; with hotbar 1.6.1 and 1.7.0).
 * Such a sprite belongs to no atlas in use, so its upload is skipped; the current sprites upload as before.
 */
@Mixin(value = PhantomSpriteManager.class, remap = false)
public abstract class RailsPhantomSpriteMixin {
    @Unique
    private static final Log haven_compat$LOG = new Log("[phantom tracks] ");

    @WrapOperation(method = "renderTick", at = @At(value = "INVOKE",
            target = "Lcom/railwayteam/railways/mixin_interfaces/IPotentiallyInvisibleSpriteContents;railways$uploadFrame(Z)V"))
    private static void haven_compat$skipStale(IPotentiallyInvisibleSpriteContents sprite, boolean visible, Operation<Void> original) {
        try {
            original.call(sprite, visible);
        } catch (IndexOutOfBoundsException e) {
            haven_compat$LOG.warnOnce("stale", "skipped a phantom track sprite of a replaced atlas ({})", e.getMessage());
        }
    }
}
