package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.ntgl.NtglKeyQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NTGL's unread key presses are dropped at the end of its key handling; see {@link NtglKeyQueue}. */
@Pseudo
@Mixin(targets = "com.nukateam.ntgl.client.handlers.InputHandler", remap = false)
public abstract class NtglKeyQueueMixin {
    @Inject(method = "handleKeys()V", at = @At("RETURN"), require = 0)
    private static void haven_compat$drain(CallbackInfo ci) {
        NtglKeyQueue.drain();
    }
}
