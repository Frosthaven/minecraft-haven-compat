package dev.frosthaven.havencompat.portals;

/** The rule of {@link MountCrossing} as plain numbers, testable without the game (as {@link CrossingBack}). */
public final class MountCorrection {
    /** blocks: a correction this near a spot before a crossing belongs to that spot */
    static final double NEAR = 8;

    private MountCorrection() {}

    /** The correction {@code at} is for the spot before a crossing: near it, and in another dimension or nearer it than to {@code now}. */
    public static boolean towardBefore(double[] at, double[] before, boolean otherDimension, double[] now) {
        double toBefore = dist(at, before);
        return toBefore <= NEAR && (otherDimension || toBefore < dist(at, now));
    }

    private static double dist(double[] a, double[] b) {
        double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return Math.sqrt(x * x + y * y + z * z);
    }
}
