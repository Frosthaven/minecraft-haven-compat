package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havencompat.keys.MacDropStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** macOS only: the drop key over an inventory slot throws the whole stack with Control too; see {@link MacDropStack}. */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public abstract class MacDropStackScreenMixin {
    @ModifyExpressionValue(method = "keyPressed",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasControlDown()Z"))
    private boolean haven_compat$wholeStack(boolean vanilla) {
        return MacDropStack.wholeStack(vanilla);
    }
}
