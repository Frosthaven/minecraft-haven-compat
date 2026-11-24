package dev.frosthaven.havencompat.portals;

/** The rule of {@link MountCrossing} as plain numbers, testable without the game (as {@link CrossingBack}). */
public final class MountCorrection {
    /** blocks: a correction this near a spot before a crossing belongs to that spot */
    static final double NEAR = 8;
    /** a stale correction is one the server sent before the crossing request reached it: a few packets at most. More of
     *  them means the server holds the mount there, and dropping them only keeps it refusing (2026-11-24, 20 in 1.4 s) */
    static final int MAX_DROPS = 6;

    private MountCorrection() {}

    /** The correction {@code at} is for the spot before a crossing: near it, and in another dimension or nearer it than to {@code now}. */
    public static boolean towardBefore(double[] at, double[] before, boolean otherDimension, double[] now) {
        double toBefore = dist(at, before);
        return toBefore <= NEAR && (otherDimension || toBefore < dist(at, now));
    }

    /** {@link #towardBefore} for the latest crossing only (an earlier spot belongs to a crossing the mount has since
     *  undone: running back and forth, the server's real correction lay near the spot of the crossing before), and only
     *  while fewer than {@link #MAX_DROPS} corrections of this crossing were dropped. */
    public static boolean stale(double[] at, double[] latestBefore, boolean otherDimension, double[] now, int droppedThisCrossing) {
        return latestBefore != null && droppedThisCrossing < MAX_DROPS && towardBefore(at, latestBefore, otherDimension, now);
    }

    private static double dist(double[] a, double[] b) {
        double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return Math.sqrt(x * x + y * y + z * z);
    }
}
