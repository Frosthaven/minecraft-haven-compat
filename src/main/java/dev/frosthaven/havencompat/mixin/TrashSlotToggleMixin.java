package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.trash.TrashSlotLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TrashSlot's toggle key cannot hide the slot on a listed screen; see {@link TrashSlotLock}. */
@Mixin(targets = "net.blay09.mods.trashslot.client.ContainerSettings", remap = false)
public abstract class TrashSlotToggleMixin {
    @Inject(method = "setEnabled", at = @At("HEAD"), cancellable = true)
    private void haven_compat$keepVisible(boolean enabled, CallbackInfo ci) {
        if (TrashSlotLock.refusesHide(this, enabled)) {
            ci.cancel();
        }
    }
}
