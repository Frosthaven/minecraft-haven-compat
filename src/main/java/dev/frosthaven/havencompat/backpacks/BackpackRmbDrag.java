package dev.frosthaven.havencompat.backpacks;

import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Sophisticated backpack on the cursor stashes a slot's stack when right-clicked onto it (BackpackItem's
 * overrideStackedOnOther, as a bundle does). Mouse Tweaks' right-button drag clicks every slot the mouse passes only
 * for a bundle; for any other item it clicks only empty or matching slots, so a backpack swept over items took
 * nothing. With a backpack on the cursor the drag now right-clicks each slot it passes that holds items (never an empty
 * one: that would put the backpack down), so the backpack takes them all, in chests and the inventory alike. Client
 * only; the creative inventory is left out (the backpack does not stash there).
 */
public final class BackpackRmbDrag {
    private static final Log LOG = new Log("[backpack drag] ");

    private BackpackRmbDrag() {
    }

    public static boolean isBackpack(ItemStack carried) {
        if (carried == null || carried.isEmpty()) {
            return false;
        }
        for (Class<?> c = carried.getItem().getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem")) {
                return true;
            }
        }
        return false;
    }

    public static void stash(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (slot == null || !slot.hasItem() || slot instanceof ResultSlot || mc.player == null || mc.gameMode == null
                || !(mc.screen instanceof AbstractContainerScreen<?> screen) || screen instanceof CreativeModeInventoryScreen) {
            return;
        }
        mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 1, ClickType.PICKUP, mc.player);
        LOG.once("drag", "a right-button drag with a backpack stashes each slot it passes");
    }
}
