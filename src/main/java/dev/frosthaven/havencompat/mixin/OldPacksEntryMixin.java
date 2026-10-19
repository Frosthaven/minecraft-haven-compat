package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havencompat.packs.OldPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The resource pack screen draws an outdated pack normally and turns it on without a confirmation; see {@link OldPacks}. */
@Mixin(targets = "net.minecraft.client.gui.screens.packs.TransferableSelectionList$PackEntry", remap = false)
public abstract class OldPacksEntryMixin {
    @ModifyExpressionValue(method = {"render", "handlePackSelection"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackCompatibility;isCompatible()Z"))
    private boolean haven_compat$compatible(boolean actual) {
        return OldPacks.compatible(actual);
    }
}
