package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.ntgl.NtglAltHand;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NTGL's alt key acts on the main hand only; see {@link NtglAltHand}. require = 0: another NTGL build keeps its own. */
@Pseudo
@Mixin(targets = "com.nukateam.ntgl.client.handlers.InputHandler", remap = false)
public abstract class NtglAltHandMixin {
    @Inject(method = "handleInput(Lcom/nukateam/ntgl/common/data/WeaponData;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/client/KeyMapping;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void haven_compat$altMainHandOnly(@Coerce Object data, InteractionHand hand, KeyMapping key, CallbackInfo ci) {
        if (NtglAltHand.skip(hand, key)) ci.cancel();
    }
}
