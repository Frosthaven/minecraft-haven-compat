package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server side. The client teleports itself when it crosses a portal and then asks the server. Immersive Portals refuses
 * the crossing when the server still waits for the client to confirm an earlier position correction ("has awaiting
 * teleport"; a high ping makes that window long) and puts the player at the server's position. That position is the
 * client's last move, a few centimetres behind the portal plane, so the player stands in front of the frame's reverse
 * portal: a small step back goes through that one and the player leaves the destination portal on the wrong side
 * (11 of 13 refusals in a client log, 2026-10-16/17). For that refusal only, the player moves along the portal's normal
 * to {@link #MARGIN} in front of the plane ({@link CrossingBack}), so the next step forward crosses the same portal
 * again. Other refusal reasons, another dimension, a portal not found or a point inside blocks keep Immersive Portals'
 * own position. The portal's origin and normal come from its public {@code getOriginPos()} / {@code getNormal()}.
 */
public final class RefusedCrossing {
    static final String AWAITING = "has awaiting teleport";
    static final double MARGIN = 0.1;
    private static final Log LOG = new Log("[portals] ");

    private RefusedCrossing() {
    }

    /** The feet position the refused player goes to; {@code upstream} is Immersive Portals' own (the server position). */
    public static Vec3 target(ServerPlayer player, ResourceKey<Level> dimensionBefore, UUID portalId, String reason, Vec3 upstream) {
        if (!AWAITING.equals(reason) || player.level().dimension() != dimensionBefore) {
            return upstream;
        }
        try {
            Entity portal = player.serverLevel().getEntity(portalId);
            if (portal == null) {
                return upstream;
            }
            Vec3 origin = vec(portal, "getOriginPos");
            Vec3 normal = vec(portal, "getNormal");
            Vec3 eyeNow = player.getEyePosition();
            double[] eye = CrossingBack.eye(new double[]{eyeNow.x, eyeNow.y, eyeNow.z},
                    new double[]{origin.x, origin.y, origin.z}, new double[]{normal.x, normal.y, normal.z}, MARGIN);
            if (eye == null) {
                return upstream;
            }
            Vec3 feet = upstream.add(eye[0] - eyeNow.x, eye[1] - eyeNow.y, eye[2] - eyeNow.z);
            if (!player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) {
                return upstream;
            }
            LOG.info("refused crossing ({}): {} goes back in front of the portal, {} instead of {}", reason,
                    player.getGameProfile().getName(), short3(feet), short3(upstream));
            return feet;
        } catch (Throwable t) {
            LOG.error("target", "refused crossing left to Immersive Portals", t);
            return upstream;
        }
    }

    private static Vec3 vec(Entity portal, String getter) throws ReflectiveOperationException {
        Method m = portal.getClass().getMethod(getter);
        return (Vec3) m.invoke(portal);
    }

    private static String short3(Vec3 v) {
        return String.format("%.3f %.3f %.3f", v.x, v.y, v.z);
    }
}
