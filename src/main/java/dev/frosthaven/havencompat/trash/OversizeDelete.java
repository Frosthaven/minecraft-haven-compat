package dev.frosthaven.havencompat.trash;

import dev.frosthaven.havencompat.Log;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * TrashSlot deletes a slot (delete keys, server side) by clicking it up onto the cursor and trashing the cursor only when
 * it matches the whole slot. A slot over one stack (Sophisticated stack upgrades: thousands in a slot) gives the cursor
 * one stack, the match fails and that stack stays on the cursor. Such a slot is now clicked up stack by stack and each
 * stack trashed until it is empty, all in the one packet (one tick); the trash slot keeps the last stack (what undo
 * gives back).
 */
public final class OversizeDelete {
    private static final Log LOG = new Log("[trash slot] ");
    /** Safety stop: 4096 stacks of 64 covers any upgraded slot. */
    private static final int MAX_STACKS = 4096;

    private OversizeDelete() {
    }

    public static boolean applies(AbstractContainerMenu menu, int slotNumber) {
        if (slotNumber < 0 || !menu.isValidSlotIndex(slotNumber)) {
            return false;
        }
        ItemStack stack = menu.getSlot(slotNumber).getItem();
        return !stack.isEmpty() && stack.getCount() > stack.getMaxStackSize();
    }

    public static boolean delete(Player player, AbstractContainerMenu menu, int slotNumber) {
        Slot slot = menu.getSlot(slotNumber);
        ItemStack wanted = slot.getItem().copy();
        ItemStack last = ItemStack.EMPTY;
        int deleted = 0;
        for (int i = 0; i < MAX_STACKS && !slot.getItem().isEmpty(); i++) {
            menu.clicked(slotNumber, 0, ClickType.PICKUP, player);
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty() || !ItemStack.isSameItemSameComponents(carried, wanted)) {
                break;   // the slot gave nothing, or something else: leave it on the cursor, as TrashSlot does
            }
            menu.setCarried(ItemStack.EMPTY);
            last = carried;
            deleted += carried.getCount();
        }
        if (last.isEmpty()) {
            return false;
        }
        try {
            Class.forName("net.blay09.mods.trashslot.TrashHelper").getMethod("setTrashItem", Player.class, ItemStack.class).invoke(null, player, last);
        } catch (ReflectiveOperationException e) {
            LOG.error("oversize", "TrashSlot's TrashHelper.setTrashItem not found; the deleted items are gone without an undo", e);
        }
        LOG.once("oversize", "deleted {} {} from an oversized slot ({} in it)", deleted, wanted.getItem(), wanted.getCount());
        menu.broadcastChanges();
        return true;
    }
}
