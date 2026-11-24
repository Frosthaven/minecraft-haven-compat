package dev.frosthaven.havencompat.animations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeedStepTest {
    /** playerAnimator 2.0.4's SpeedModifier.step loop with the guard in front; returns the loop turns, -1 past a bound. */
    private static int turns(float delta, float speed, float shifted) {
        if (!SpeedStep.safe(delta, speed, shifted)) {
            speed = SpeedStep.speed(speed);
            shifted = SpeedStep.shifted(shifted);
            delta = SpeedStep.delta(delta, speed, shifted);
        }
        delta *= speed;
        delta += shifted;
        int turns = 0;
        while (delta > 1f) {
            delta--;
            if (++turns > 1_000_000) return -1;
        }
        return turns;
    }

    @Test
    void infiniteSpeedEnds() {
        // NTGL: animation length / reload duration 0.
        assertTrue(turns(0.4f, Float.POSITIVE_INFINITY, 0f) >= 0);
        assertEquals(1f, SpeedStep.speed(Float.POSITIVE_INFINITY));
    }

    @Test
    void notANumberEnds() {
        assertTrue(turns(Float.NaN, 1f, 0f) >= 0);
        assertTrue(turns(0.5f, Float.NaN, 0f) >= 0);
        assertTrue(turns(0.5f, 1f, Float.NaN) >= 0);
    }

    @Test
    void hugeFiniteTotalIsCapped() {
        // 2^24 and up: delta-- no longer changes the float.
        int turns = turns(1f, 3.0e7f, 0f);
        assertTrue(turns >= 0 && turns <= SpeedStep.MAX_TICKS);
    }

    @Test
    void normalStepsAreUntouched() {
        assertTrue(SpeedStep.safe(0.3f, 2.5f, 0.6f));
        assertTrue(SpeedStep.safe(1f, 0f, 0.2f));
        assertTrue(SpeedStep.safe(0.5f, -3f, 0f));
        assertEquals(1, turns(0.5f, 2.5f, 0.6f));
    }

    @Test
    void unsafeValuesAreSeen() {
        assertFalse(SpeedStep.safe(0.1f, Float.POSITIVE_INFINITY, 0f));
        assertFalse(SpeedStep.safe(1f, 1000f, 0f));
    }
}
