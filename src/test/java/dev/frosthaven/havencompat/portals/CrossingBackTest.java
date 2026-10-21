package dev.frosthaven.havencompat.portals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class CrossingBackTest {
    @Test
    void movesFurtherOutOfTheFront() {
        // Crossed an east-west plane at x = -986.5 walking east: before is in front (west), now is behind (east).
        double[] eye = CrossingBack.eye(new double[]{-986.48, 64.62, 555.02}, new double[]{-986.51, 64.62, 555.02}, 0.05);
        assertArrayEquals(new double[]{-986.56, 64.62, 555.02}, eye, 1e-9);
    }

    @Test
    void followsTheCrossingDirection() {
        // Falling through a floor portal: back out means up.
        double[] eye = CrossingBack.eye(new double[]{10, 63.9, 10}, new double[]{10, 64.3, 10}, 0.1);
        assertArrayEquals(new double[]{10, 64.4, 10}, eye, 1e-9);
    }

    @Test
    void noMovementKeepsThePointBefore() {
        double[] before = {1, 2, 3};
        assertArrayEquals(before, CrossingBack.eye(new double[]{1, 2, 3}, before, 0.05), 1e-12);
    }
}
