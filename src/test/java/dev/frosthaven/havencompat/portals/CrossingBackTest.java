package dev.frosthaven.havencompat.portals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CrossingBackTest {
    // The test run's portal: plane x = -43.5, normal west, crossed walking east; refused 2 cm behind the plane.
    private static final double[] ORIGIN = {-43.5, 201.5, -95.0};
    private static final double[] WEST = {-1, 0, 0};

    @Test
    void movesTheEyeInFrontOfThePlane() {
        double[] eye = CrossingBack.eye(new double[]{-43.48, 201.62, -95.0}, ORIGIN, WEST, 0.1);
        assertArrayEquals(new double[]{-43.6, 201.62, -95.0}, eye, 1e-9);
    }

    @Test
    void movesOnlyAlongTheNormal() {
        // A floor portal (normal up) crossed falling: the eye goes up, x and z stay.
        double[] eye = CrossingBack.eye(new double[]{3.2, 63.97, 7.9}, new double[]{3, 64, 8}, new double[]{0, 1, 0}, 0.1);
        assertArrayEquals(new double[]{3.2, 64.1, 7.9}, eye, 1e-9);
    }

    @Test
    void leavesAnEyeAlreadyInFront() {
        assertNull(CrossingBack.eye(new double[]{-43.7, 201.62, -95.0}, ORIGIN, WEST, 0.1));
    }

    @Test
    void leavesAnEyeFarBehind() {
        assertNull(CrossingBack.eye(new double[]{-42.0, 201.62, -95.0}, ORIGIN, WEST, 0.1));
    }
}
