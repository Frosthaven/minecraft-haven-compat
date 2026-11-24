package dev.frosthaven.havencompat.animations;

/**
 * playerAnimator 2.0.4's {@code SpeedModifier.step(delta)} runs {@code delta *= speed; delta += shiftedDelta; while
 * (delta > 1) { delta--; tick(); }}. Its constructor refuses a speed that is not finite, but {@code speed} is a public
 * field. NTGL 3.2.0 sets it to {@code animationLength / duration} ({@code PlayerAnimationHelper.playAnim}); a reload
 * duration of 0 (a per-cartridge gun the client sees as full, e.g. at an ammo switch) gives +Infinity, the loop never
 * ends and the render thread hangs for good. A total of 2^24 or more also never ends ({@code delta--} stops changing it).
 * {@code mixin/AnimSpeedGuardMixin} gives the step values that end: a speed that is not finite plays at 1, a broken
 * remainder starts at 0, and one step advances at most {@link #MAX_TICKS} animation ticks. Pure (unit tested).
 */
public final class SpeedStep {
    /** Animation ticks one step may advance; a real step advances about |speed| + 1. */
    public static final float MAX_TICKS = 200f;

    private SpeedStep() {}

    /** True when step(delta) with these values ends within {@link #MAX_TICKS} loop turns. */
    public static boolean safe(float delta, float speed, float shifted) {
        if (!Float.isFinite(delta) || !Float.isFinite(speed) || !Float.isFinite(shifted)) return false;
        float total = delta * speed + shifted;
        return Float.isFinite(total) && total <= MAX_TICKS;
    }

    /** The speed to keep: a speed that is not finite becomes 1 (the animation's own pace). */
    public static float speed(float speed) {
        return Float.isFinite(speed) ? speed : 1f;
    }

    /** The remainder to keep: step leaves it at 1 or less; anything else starts again at 0. */
    public static float shifted(float shifted) {
        return Float.isFinite(shifted) && shifted <= 1f ? shifted : 0f;
    }

    /** The delta to step with (speed and shifted already fixed): delta * speed + shifted at most {@link #MAX_TICKS}. */
    public static float delta(float delta, float speed, float shifted) {
        if (!Float.isFinite(delta)) return 0f;
        float total = delta * speed + shifted;
        if (Float.isFinite(total) && total <= MAX_TICKS) return delta;
        return speed == 0f ? 0f : (MAX_TICKS - shifted) / speed;
    }
}
