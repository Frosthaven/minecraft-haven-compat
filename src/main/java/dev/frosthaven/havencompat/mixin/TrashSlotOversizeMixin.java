package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.trash.OversizeDelete;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TrashSlot's delete keys on a slot holding more than one stack (stack upgrades); see {@link OversizeDelete}. */
@Pseudo
@Mixin(targets = "net.blay09.mods.trashslot.network.MessageDeleteFromSlot", remap = false)
public abstract class TrashSlotOversizeMixin {
    @Inject(method = "attemptDeleteFromSlot", at = @At("HEAD"), cancellable = true)
    private static void havencompat$oversize(Player player, AbstractContainerMenu menu, int slotNumber, CallbackInfoReturnable<Boolean> cir) {
        if (OversizeDelete.applies(menu, slotNumber)) {
            cir.setReturnValue(OversizeDelete.delete(player, menu, slotNumber));
        }
    }
}
