package dev.frosthaven.havencompat.jade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotchMoveTest {
    @Test
    void thePackValueMovesToTheMacValue() {
        assertTrue(NotchMove.shouldMove(1.0f, 1.0f, 0.94f, false));
    }

    @Test
    void aBoxThePlayerMovedStays() {
        assertFalse(NotchMove.shouldMove(0.8f, 1.0f, 0.94f, false));
    }

    @Test
    void forceMovesEvenAPlayersBox() {
        assertTrue(NotchMove.shouldMove(0.8f, 1.0f, 0.94f, true));
    }

    @Test
    void alreadyThereIsNoMove() {
        assertFalse(NotchMove.shouldMove(0.94f, 1.0f, 0.94f, true));
        assertFalse(NotchMove.shouldMove(0.9400001f, 1.0f, 0.94f, false));
    }
}
