package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server side. The client teleports itself when it crosses a portal and then asks the server. Immersive Portals refuses
 * the crossing when the server still waits for the client to confirm an earlier position correction ("has awaiting
 * teleport"; a high ping makes that window long) and puts the player back at the server's position. That position is
 * the client's last move, just behind the portal plane, so the player stands in the plane on the side of the frame's
 * reverse portal: a small step back then goes through that one and the player leaves the destination portal on the
 * wrong side (11 of 13 refusals in a client log, 2026-10-16/17). For that refusal only, the player goes back to the
 * eye position the client sent from before the crossing, {@link #MARGIN} further out of the portal's front
 * ({@link CrossingBack}), so the next step forward crosses the same portal again. Other refusal reasons, another
 * dimension, a far point or a point inside blocks keep Immersive Portals' own position.
 */
public final class RefusedCrossing {
    static final String AWAITING = "has awaiting teleport";
    static final double MARGIN = 0.05;
    private static final Log LOG = new Log("[portals] ");

    private RefusedCrossing() {
    }

    /** The feet position the refused player goes to; {@code upstream} is Immersive Portals' own (the server position). */
    public static Vec3 target(ServerPlayer player, ResourceKey<Level> dimensionBefore, Vec3 eyeBefore, String reason,
                              Vec3 upstream) {
        if (!AWAITING.equals(reason) || player.level().dimension() != dimensionBefore) {
            return upstream;
        }
        Vec3 eyeNow = player.getEyePosition();
        if (eyeNow.distanceToSqr(eyeBefore) > 4.0) {
            return upstream;
        }
        double[] eye = CrossingBack.eye(new double[]{eyeNow.x, eyeNow.y, eyeNow.z},
                new double[]{eyeBefore.x, eyeBefore.y, eyeBefore.z}, MARGIN);
        for (Vec3 e : new Vec3[]{new Vec3(eye[0], eye[1], eye[2]), eyeBefore}) {
            Vec3 feet = e.subtract(0, player.getEyeHeight(), 0);
            if (player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) {
                LOG.info("refused crossing ({}): {} goes back in front of the portal, {} instead of {}", reason,
                        player.getGameProfile().getName(), short3(feet), short3(upstream));
                return feet;
            }
        }
        return upstream;
    }

    private static String short3(Vec3 v) {
        return String.format("%.3f %.3f %.3f", v.x, v.y, v.z);
    }
}
