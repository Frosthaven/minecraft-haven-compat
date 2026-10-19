package dev.frosthaven.havencompat.defaults;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackOrderTest {
    @Test
    void aPackBelowItsAnchorMovesRightAboveIt() {
        List<String> packs = new ArrayList<>(List.of("vanilla", "fa", "crops", "ve", "containers"));
        assertTrue(PackOrder.moveAbove(packs, "fa", "ve"));
        assertEquals(List.of("vanilla", "crops", "ve", "fa", "containers"), packs);
    }

    @Test
    void theRulesInSequenceRebuildTheFreshAnimationsStack() {
        List<String> packs = new ArrayList<>(List.of("ext", "player", "fa", "ve", "containers"));
        PackOrder.moveAbove(packs, "fa", "ve");
        PackOrder.moveAbove(packs, "ext", "fa");
        PackOrder.moveAbove(packs, "player", "ext");
        assertEquals(List.of("ve", "fa", "ext", "player", "containers"), packs);
    }

    @Test
    void aPackAlreadyAboveStays() {
        List<String> packs = new ArrayList<>(List.of("ve", "crops", "fa"));
        assertFalse(PackOrder.moveAbove(packs, "fa", "ve"));
        assertEquals(List.of("ve", "crops", "fa"), packs);
    }

    @Test
    void aPackThePlayerTurnedOffIsNotSettled() {
        List<String> packs = new ArrayList<>(List.of("ve", "crops"));
        assertFalse(PackOrder.moveAbove(packs, "fa", "ve"));
        assertFalse(PackOrder.settled(packs, "fa", "ve"));
    }
}
