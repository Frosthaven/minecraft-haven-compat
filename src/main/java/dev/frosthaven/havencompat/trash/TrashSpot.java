package dev.frosthaven.havencompat.trash;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Where the trash slot goes on a screen: under the bottom-right corner of the player's hotbar panel, in TrashSlot's
 * units (offset of the slot's top-left corner from the centre of the screen's image box, anchors 0.5). Vanilla panels
 * end 8 px past the last hotbar slot's 16 px item area on the right and below; TrashSlot's lone slot is 28 px wide. On a
 * vanilla screen this is TrashSlot's own default, so only screens whose box is bigger than their inventory panel move.
 */
final class TrashSpot {
    private static final int PANEL_EDGE = 16 + 8;
    private static final int SLOT_WIDTH = 28;
    /** Panels drawn a pixel or two off the vanilla edge keep TrashSlot's spot, where it draws the slot as a tab of the box. */
    private static final int TOLERANCE = 2;

    private TrashSpot() {
    }

    /** {@code [x, y]} in TrashSlot's units, or null when the screen shows no hotbar. */
    static int[] underHotbar(AbstractContainerScreen<?> screen) {
        Slot last = null;
        for (Slot slot : screen.getMenu().slots) {
            if (!(slot.container instanceof Inventory) || slot.getContainerSlot() > 8 || !slot.isActive()) {
                continue;
            }
            if (slot.x < -16 || slot.y < -16 || slot.x > screen.getXSize() + 64 || slot.y > screen.getYSize() + 64) {
                continue;
            }
            if (last == null || slot.x > last.x) {
                last = slot;
            }
        }
        if (last == null) {
            return null;
        }
        return spot(last.x, last.y, screen.getXSize(), screen.getYSize());
    }

    /** True when {@code spot} is within {@link #TOLERANCE} px of TrashSlot's default on both axes. */
    static boolean close(int[] spot, int defaultX, int defaultY) {
        return Math.abs(spot[0] - defaultX) <= TOLERANCE && Math.abs(spot[1] - defaultY) <= TOLERANCE;
    }

    /** The slot spot for a hotbar whose rightmost slot is at {@code slotX, slotY} in an image box {@code width} x {@code height}. */
    static int[] spot(int slotX, int slotY, int width, int height) {
        return new int[] {slotX + PANEL_EDGE - SLOT_WIDTH - (int) (width * 0.5F), slotY + PANEL_EDGE - (int) (height * 0.5F)};
    }
}
