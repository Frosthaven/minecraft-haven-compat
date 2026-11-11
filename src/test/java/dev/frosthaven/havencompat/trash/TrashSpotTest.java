package dev.frosthaven.havencompat.trash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrashSpotTest {
    /** A vanilla screen (hotbar's last slot at 152,142 in a 176 x 166 box) keeps TrashSlot's own default, 60,83. */
    @Test
    void vanillaScreenKeepsTrashSlotsDefault() {
        assertArrayEquals(new int[] {176 / 2 - 28, 166 / 2}, TrashSpot.spot(152, 142, 176, 166));
    }

    /** A box wider and taller than its inventory panel: the slot follows the hotbar, not the box corner. */
    @Test
    void biggerBoxFollowsTheHotbar() {
        // hotbar's last slot at 200,180 in a 300 x 260 box: panel corner at 224,204, so the slot's left edge sits 28 to its left
        assertArrayEquals(new int[] {224 - 28 - 150, 204 - 130}, TrashSpot.spot(200, 180, 300, 260));
    }

    /** A vanilla chest's panel ends 1 px off the rule: it keeps TrashSlot's spot; a real gap of 7 px does not. */
    @Test
    void nearDefaultKeepsTrashSlotsSpot() {
        assertTrue(TrashSpot.close(new int[] {60, 83}, 60, 84));
        assertFalse(TrashSpot.close(new int[] {53, 77}, 60, 84));
    }
}
