package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.mekanism.ModeHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mekanism's item mode key (key.mekanism.mode, its key-down action: a lambda in MekanismKeyHandler's static init)
 * picks the hand by sneaking; see {@link ModeHand}. require = 0: another Mekanism build may name the lambda otherwise,
 * then Mekanism's own choice stays.
 */
@Pseudo
@Mixin(targets = "mekanism.client.key.MekanismKeyHandler", remap = false)
public abstract class MekanismModeHandMixin {
    @Inject(method = "lambda$static$0(Lnet/minecraft/client/KeyMapping;Ljava/lang/Boolean;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void haven_compat$modeHand(CallbackInfo ci) {
        if (ModeHand.press()) ci.cancel();
    }
}
