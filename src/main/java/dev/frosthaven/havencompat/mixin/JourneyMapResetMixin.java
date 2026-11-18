package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.journeymap.JourneyMapReset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JourneyMap's reset at a dimension change does not read every config file again; see {@link JourneyMapReset}. */
@Pseudo
@Mixin(targets = "journeymap.client.JourneymapClient", remap = false)
public abstract class JourneyMapResetMixin {
    @Inject(method = "reset", at = @At("HEAD"))
    private void haven_compat$resetStarts(CallbackInfo ci) {
        JourneyMapReset.resetStarts();
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void haven_compat$resetEnds(CallbackInfo ci) {
        JourneyMapReset.resetEnds();
    }

    @Inject(method = "loadConfigProperties", at = @At("HEAD"), cancellable = true)
    private void haven_compat$once(CallbackInfo ci) {
        if (JourneyMapReset.skipReload()) ci.cancel();
    }
}
