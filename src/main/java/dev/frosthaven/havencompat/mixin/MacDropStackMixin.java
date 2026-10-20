package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.keys.MacDropStack;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * macOS only: the drop key in the world drops the whole stack with Control too; see {@link MacDropStack}. MacOS Input
 * Fixes replaces the same argument with its own modifier (Cmd or Control, its option); priority 1100 applies this
 * handler after it, so Control still counts in its Cmd mode.
 */
@Mixin(value = Minecraft.class, remap = false, priority = 1100)
public abstract class MacDropStackMixin {
    @ModifyArg(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;drop(Z)Z"), index = 0)
    private boolean haven_compat$wholeStack(boolean fullStack) {
        return MacDropStack.wholeStack(fullStack);
    }
}
