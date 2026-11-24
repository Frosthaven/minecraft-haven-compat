package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.ntgl.NtglAmmoSelectHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NTGL's ammo select key acts only with a gun in its hand; see {@link NtglAmmoSelectHand}. require = 0. */
@Pseudo
@Mixin(targets = "com.nukateam.ntgl.client.handlers.InputHandler", remap = false)
public abstract class NtglAmmoSelectHandMixin {
    @Inject(method = "selectAmmoKeyPressed()V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void haven_compat$gunInHand(CallbackInfo ci) {
        if (NtglAmmoSelectHand.skip()) ci.cancel();
    }
}
