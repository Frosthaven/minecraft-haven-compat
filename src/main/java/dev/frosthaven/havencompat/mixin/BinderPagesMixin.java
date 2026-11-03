package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.binder.BinderFix;
import dev.frosthaven.havencompat.binder.BinderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The open binder's last page follows the view, so its own page buttons and label stay right; see {@link BinderFix}. */
@Mixin(targets = BinderTargets.MENU_CLASS, remap = false)
public abstract class BinderPagesMixin {
    @Inject(method = "getMaxPage", at = @At("HEAD"), cancellable = true)
    private void haven_compat$viewPages(CallbackInfoReturnable<Integer> cir) {
        Integer last = BinderFix.lastPage(this);
        if (last != null) cir.setReturnValue(last);
    }
}
