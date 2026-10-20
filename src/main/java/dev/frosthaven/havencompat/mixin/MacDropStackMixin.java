package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havencompat.keys.MacDropStack;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** macOS only: the drop key in the world drops the whole stack with Control too; see {@link MacDropStack}. */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MacDropStackMixin {
    @ModifyExpressionValue(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;hasControlDown()Z"))
    private boolean haven_compat$wholeStack(boolean vanilla) {
        return MacDropStack.wholeStack(vanilla);
    }
}
