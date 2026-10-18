package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.updates.UpdateNags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create Deep Seas' {@code UpdateChecker.check()} asks Modrinth for the newest version at client start and, when it
 * is newer than the installed one, opens {@code DeepSeasUpdateScreen} in front of the title screen. Off: no request,
 * no screen, no "Update Available" button; see {@link UpdateNags}.
 */
@Mixin(targets = UpdateNags.DEEP_SEAS_TARGET, remap = false)
public abstract class DeepSeasUpdateCheckMixin {
    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private static void haven_compat$skip(CallbackInfo ci) {
        UpdateNags.skipped("Create Deep Seas");
        ci.cancel();
    }
}
