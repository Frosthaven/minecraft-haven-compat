package dev.frosthaven.havencompat.portals;

/**
 * Where a refused portal crossing puts the player back ({@link RefusedCrossing}), as plain numbers so it is testable
 * without the game. Immersive Portals teleports when the eye moves from the portal's front (the side its normal
 * points to) to its back. The refused player's eye stands a few centimetres behind the plane; it moves along the
 * normal to {@code margin} in front of it.
 */
public final class CrossingBack {
    private CrossingBack() {
    }

    /**
     * The eye point {@code margin} in front of the plane through {@code origin} with unit {@code normal}; null when
     * the eye is already that far in front, or more than a block behind (not a fresh crossing).
     */
    public static double[] eye(double[] eye, double[] origin, double[] normal, double margin) {
        double d = (eye[0] - origin[0]) * normal[0] + (eye[1] - origin[1]) * normal[1] + (eye[2] - origin[2]) * normal[2];
        if (d >= margin || d < -1.0) {
            return null;
        }
        double k = margin - d;
        return new double[]{eye[0] + normal[0] * k, eye[1] + normal[1] * k, eye[2] + normal[2] * k};
    }
}
