package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.binder.BinderFix;
import dev.frosthaven.havencompat.binder.BinderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A card slot of the open binder is active when the view shows it on the current page; see {@link BinderFix}. */
@Mixin(targets = BinderTargets.SLOT_CLASS, remap = false)
public abstract class BinderSlotMixin {
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void haven_compat$viewActive(CallbackInfoReturnable<Boolean> cir) {
        Boolean active = BinderFix.active(this);
        if (active != null) cir.setReturnValue(active);
    }
}
