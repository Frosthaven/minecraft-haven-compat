package dev.frosthaven.havencompat.defaults;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionChoiceTest {
    @Test
    void anUnsetKeyIsWritten() {
        assertEquals(OptionChoice.WRITE, OptionChoice.decide(null, null, "1"));
    }

    @Test
    void thePacksValueAlreadyThereIsOnlyRecorded() {
        assertEquals(OptionChoice.ALREADY, OptionChoice.decide("1", null, "1"));
    }

    @Test
    void aValueThePlayerSetStays() {
        assertEquals(OptionChoice.PLAYERS, OptionChoice.decide("3", null, "1"));
        assertEquals(OptionChoice.PLAYERS, OptionChoice.decide("3", "1", "1"));
    }

    @Test
    void aPackChangeMovesWhatThisModWrote() {
        assertEquals(OptionChoice.WRITE, OptionChoice.decide("1", "1", "0"));
    }
}
