package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.carryon.CarrySlot;
import net.minecraft.world.entity.player.Inventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Carry On's carry-data sync moves the hotbar selection only while carrying; see {@link CarrySlot}. */
@Mixin(targets = "tschipp.carryon.networking.clientbound.ClientboundSyncCarryDataPacket", remap = false)
public abstract class CarrySlotMixin {
    @Redirect(method = "handle", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = Opcodes.PUTFIELD))
    private void haven$keepSelection(Inventory inventory, int slot) {
        if (CarrySlot.allowed(this, slot, inventory.selected)) inventory.selected = slot;
    }
}
