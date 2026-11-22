package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A rider's view at the moment a Create train carriage takes them through an Immersive Portals portal. A player on foot
 * is moved to the other side by the client the frame the eyes cross the portal's plane. A rider is moved by the server,
 * with the carriage, and the packet comes a few frames after the eyes have crossed: for those frames the camera was past
 * the plane on the near side, inside whatever stands behind the portal (a view of the inside of a block of andesite at a
 * portal dug into a hill, Shane's screenshot, 2026-11-22).
 * <p>
 * While the local player rides a carriage that is crossing (Immersive Portals' own record of the crossing: a point on
 * the portal plane and the normal toward the side the carriage is still on) and the first-person camera is past that
 * plane, {@link #hold} gives the camera's place {@value #KEEP} blocks in front of the plane: the view is into the
 * portal, at the other side, as in the frame before a crossing on foot. Reflection only; a failure is logged once and
 * the camera is left alone.
 */
public final class PortalEye {
    private static final Log LOG = new Log("[portal eye] ");
    private static final double KEEP = 0.08;
    private static Class<?> carriage;
    private static Method crossingOf, keepPoint, keepNormal, renderPoint, renderDirection;
    private static boolean broken;
    private static int held;

    private PortalEye() {}

    /** The place the camera should be, or null to leave it. */
    public static Vec3 hold(Entity viewer, boolean detached, Vec3 camera) {
        if (broken || detached || viewer == null || viewer != Minecraft.getInstance().player || Boolean.getBoolean("haven_compat.noPortalEye")) return null;
        Entity vehicle = viewer.getVehicle();
        if (vehicle == null) return null;
        try {
            if (carriage == null) {
                carriage = Class.forName("com.simibubi.create.content.trains.entity.CarriageContraptionEntity");
                Class<?> client = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPCreateCarriageCrossingClient");
                crossingOf = client.getMethod("crossingOf", Entity.class);
                Class<?> crossing = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPCreateCarriageCrossing$Crossing");
                keepPoint = crossing.getMethod("keepPoint");
                keepNormal = crossing.getMethod("keepNormal");
                Class<?> ships = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPCreateTrainShipsClient");
                renderPoint = ships.getDeclaredMethod("renderPoint", Entity.class, Vec3.class);
                renderDirection = ships.getDeclaredMethod("renderDirection", Entity.class, Vec3.class);
                renderPoint.setAccessible(true);
                renderDirection.setAccessible(true);
            }
            if (!carriage.isInstance(vehicle)) return null;
            Object crossing = crossingOf.invoke(null, vehicle);
            if (crossing == null) { held = 0; return null; }
            Vec3 point = (Vec3) renderPoint.invoke(null, vehicle, keepPoint.invoke(crossing));
            Vec3 normal = (Vec3) renderDirection.invoke(null, vehicle, keepNormal.invoke(crossing));
            double side = camera.subtract(point).dot(normal);   // above 0: on the side the carriage is still on
            if (side >= KEEP || side < -3) { held = 0; return null; }   // far past it: not this portal's business
            if (held++ == 0) LOG.info("the rider's eyes are past the portal plane before the game moved them across; the camera is held at the plane");
            return camera.add(normal.scale(KEEP - side));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            LOG.error("hold", "could not hold a train rider's camera at a portal plane; it is left alone", e);
            return null;
        }
    }
}
