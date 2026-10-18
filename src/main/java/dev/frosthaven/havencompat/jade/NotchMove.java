package dev.frosthaven.havencompat.jade;

/** The Jade notch fix's one decision, pure (no Minecraft classes) so it is unit tested. */
public final class NotchMove {
    static final float EPSILON = 0.001f;

    private NotchMove() {
    }

    /**
     * Move when the box is not at {@code macos} yet and still at the pack's value (or {@code force}); a box the player
     * moved elsewhere stays.
     */
    public static boolean shouldMove(float current, float packValue, float macos, boolean force) {
        if (Math.abs(current - macos) < EPSILON) {
            return false;
        }
        return force || Math.abs(current - packValue) < EPSILON;
    }
}
