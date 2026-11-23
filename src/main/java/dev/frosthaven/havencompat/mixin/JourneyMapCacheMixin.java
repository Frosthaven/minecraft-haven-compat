package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.journeymap.JourneyMapReset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JourneyMap's data cache is not emptied at a dimension change. The purge in its reset closes every cached region
 * image on the client thread (0.1 s at a portal crossing, 2026-11-22), and the images of the dimension just left are
 * wanted again at the next crossing back. The cache is keyed by dimension and has its own size and age limits, so it
 * stays within them without the purge. A session's first reset still purges. See {@link JourneyMapReset#keepTheme}.
 */
@Pseudo
@Mixin(targets = "journeymap.client.data.DataCache", remap = false)
public abstract class JourneyMapCacheMixin {
    @Inject(method = "purge", at = @At("HEAD"), cancellable = true)
    private void haven_compat$keepCache(CallbackInfo ci) {
        if (JourneyMapReset.keepTheme()) ci.cancel();
    }
}
