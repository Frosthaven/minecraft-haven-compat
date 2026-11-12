package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.backpacks.BackpackRmbDrag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse Tweaks' right-button drag with a Sophisticated backpack on the cursor; see {@link BackpackRmbDrag}. */
@Pseudo
@Mixin(targets = "yalter.mousetweaks.Main", remap = false)
public abstract class BackpackRmbDragMixin {
    @Inject(method = "rmbTweakMaybeClickSlot", at = @At("HEAD"), cancellable = true)
    private static void havencompat$backpack(Slot slot, ItemStack carried, CallbackInfo ci) {
        if (BackpackRmbDrag.isBackpack(carried)) {
            BackpackRmbDrag.stash(slot);
            ci.cancel();
        }
    }
}
