package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.trash.TrashSlotLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The settings TrashSlot uses for a screen: a listed screen's are switched on; see {@link TrashSlotLock}. */
@Mixin(targets = "net.blay09.mods.trashslot.TrashSlotSaveState", remap = false)
public abstract class TrashSlotVisibleMixin {
    @Inject(method = "getSettings", at = @At("RETURN"))
    private static void haven_compat$alwaysVisible(CallbackInfoReturnable<Object> cir) {
        TrashSlotLock.settingsReturned(cir.getReturnValue());
    }
}
