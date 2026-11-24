package dev.frosthaven.havencompat.ntgl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WheelEntriesTest {
    @Test
    void pumpShotgunKeepsOnlyPrimary() {
        // Create Gunsmithing shotgun with the pump: [secondary (scope), primary].
        assertEquals(List.of(1), WheelEntries.kept(List.of("secondary", "primary"), List.of(false, true)));
    }

    @Test
    void primaryTwiceIsOneEntry() {
        assertEquals(List.of(0), WheelEntries.kept(List.of("primary", "primary"), List.of(true, true)));
    }

    @Test
    void twoFiringModesStay() {
        assertEquals(List.of(0, 1), WheelEntries.kept(List.of("alternative", "primary"), List.of(true, true)));
    }

    @Test
    void unknownModeStays() {
        assertEquals(List.of(0, 1), WheelEntries.kept(Arrays.asList(null, "primary"), List.of(true, true)));
    }

    @Test
    void nothingLeftKeepsAll() {
        assertEquals(List.of(0, 1), WheelEntries.kept(List.of("secondary", "additional"), List.of(false, false)));
    }
}
