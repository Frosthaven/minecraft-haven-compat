package dev.frosthaven.havencompat.portals;

/**
 * Where a refused portal crossing puts the player back ({@link RefusedCrossing}), as plain numbers so it is testable
 * without the game: the eye position before the crossing, moved a little further away from the eye position after it.
 * The crossing went from {@code before} (in front of the portal) to {@code now} (behind its plane), so that direction
 * points back out of the portal's front.
 */
public final class CrossingBack {
    private CrossingBack() {
    }

    /** The eye point {@code margin} blocks beyond {@code before}, away from {@code now}; {@code before} when they are equal. */
    public static double[] eye(double[] now, double[] before, double margin) {
        double dx = before[0] - now[0], dy = before[1] - now[1], dz = before[2] - now[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            return before.clone();
        }
        double k = margin / len;
        return new double[]{before[0] + dx * k, before[1] + dy * k, before[2] + dz * k};
    }
}
