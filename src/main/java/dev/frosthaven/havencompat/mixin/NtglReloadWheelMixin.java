package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.frosthaven.havencompat.ntgl.NtglReloadWheel;
import java.util.ArrayList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NTGL's reload and unload wheels: one entry per mode that uses ammo; see {@link NtglReloadWheel}. require = 0. */
@Pseudo
@Mixin(targets = "com.nukateam.ntgl.client.handlers.InputHandler", remap = false)
public abstract class NtglReloadWheelMixin {
    @Inject(method = "reloadUnload(Z)V", at = @At(value = "INVOKE",
            target = "Lcom/nukateam/ntgl/modules/wheel/ActionWheelManager;showWheel(Ljava/util/List;Lnet/minecraft/network/chat/Component;Ljava/lang/Runnable;)V"),
            cancellable = true, require = 0)
    private static void haven_compat$realEntries(boolean reload, CallbackInfo ci, @Local ItemStack held,
            @Local(ordinal = 0) ArrayList<?> modes, @Local(ordinal = 1) ArrayList<?> actions) {
        if (NtglReloadWheel.beforeShow(actions, modes, held, reload)) ci.cancel();
    }
}
