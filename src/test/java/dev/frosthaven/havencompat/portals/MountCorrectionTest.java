package dev.frosthaven.havencompat.portals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MountCorrectionTest {
    // The test run: the horse's last Overworld spot before the plane at x -835.5, then in the Nether at x -100.86.
    private static final double[] BEFORE = {-835.229, 71.125, 1650.5};
    private static final double[] NETHER_NOW = {-100.861, 97.125, 201.5};

    @Test
    void dropsTheOldDimensionsCorrection() {
        assertTrue(MountCorrection.towardBefore(new double[]{-835.229, 71.125, 1650.5}, BEFORE, true, NETHER_NOW));
    }

    @Test
    void keepsACorrectionInTheNewDimension() {
        assertFalse(MountCorrection.towardBefore(new double[]{-100.7, 97.125, 201.5}, BEFORE, true, NETHER_NOW));
    }

    @Test
    void sameLevelPortalKeepsACorrectionNearTheMount() {
        // a portal within one level: jump from x 0 to x 40; a correction at x 39.5 belongs to the new spot
        assertFalse(MountCorrection.towardBefore(new double[]{39.5, 64, 0}, new double[]{0, 64, 0}, false, new double[]{40, 64, 0}));
        assertTrue(MountCorrection.towardBefore(new double[]{0.3, 64, 0}, new double[]{0, 64, 0}, false, new double[]{40, 64, 0}));
    }
}
