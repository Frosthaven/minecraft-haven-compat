package dev.frosthaven.havencompat.animations;

import dev.frosthaven.havencompat.Log;

/** The log of the speed step guard (rules in {@link SpeedStep}, applied by {@code mixin/AnimSpeedGuardMixin}). */
public final class AnimSpeedGuard {
    private static final Log LOG = new Log("[anim speed guard] ");

    private AnimSpeedGuard() {}

    /** One log line the first time the guard changes a step. */
    public static void acted(float delta, float speed, float shifted) {
        LOG.once("on", "a player animation step would never end (delta {}, speed {}, remainder {}); it plays at a finite speed instead",
                delta, speed, shifted);
    }
}
