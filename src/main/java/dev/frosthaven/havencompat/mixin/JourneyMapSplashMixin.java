package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.journeymap.JourneyMapSplash;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The fullscreen map opens without JourneyMap's "About" splash; see {@link JourneyMapSplash}. */
@Pseudo
@Mixin(targets = "journeymap.client.ui.fullscreen.Fullscreen", remap = false)
public abstract class JourneyMapSplashMixin {
    @Inject(method = "init()V", at = @At("HEAD"))
    private void haven_compat$splashSeen(CallbackInfo ci) {
        JourneyMapSplash.markSeen();
    }
}
