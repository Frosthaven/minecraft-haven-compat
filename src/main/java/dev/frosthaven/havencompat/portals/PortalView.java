package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.settings.SavedChoice;
import dev.frosthaven.havencompat.settings.SavedNumber;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * The player's Immersive Portal Rendering setting (Experimental Optimizations), saved on this computer.
 * <ul>
 * <li>Fully immersive (the default since 0.43.1, Shane 2026-11-25): Immersive Portals as configured.</li>
 * <li>Performance mode (the default in 0.43.0 only): the view through a portal within {@link #DISTANCE} blocks of the camera; a portal
 *     farther away, and a portal seen through another portal ({@link #nestedNow}), draws {@link PortalPlane} (vanilla's
 *     animated nether portal texture) instead. Immersive Portals' own reduced rendering cuts at a fixed 16 blocks and
 *     draws nothing past it (the pack had it on for a few hours, haven.319, and players saw the frame's back wall).</li>
 * <li>Disabled: no view through a portal, {@link PortalPlane} on every portal; teleporting stays.</li>
 * </ul>
 * 0.43.0 renamed Normal and Fast; a saved Fast is Performance mode, which adds the distance to it.
 */
public final class PortalView {
    public enum Level { IMMERSIVE, PERFORMANCE, DISABLED }

    public static final SavedChoice<Level> SETTING = new SavedChoice<>(Level.class, Level.IMMERSIVE,
            "haven-compat-portal-view.json", "portalView", "Immersive Portal Rendering", new Log("[portal view] "))
            .renamed(Map.of("NORMAL", Level.IMMERSIVE, "FAST", Level.PERFORMANCE));

    /** Performance mode: the farthest a portal may be from the camera and still show its view, in blocks. */
    public static final SavedNumber DISTANCE = new SavedNumber(24, 8, 64,
            "haven-compat-portal-distance.json", "portalViewDistance", "Portal view distance", new Log("[portal view] "));

    private PortalView() {}

    public static boolean disabled() { return SETTING.get() == Level.DISABLED; }

    private static boolean performance() { return SETTING.get() == Level.PERFORMANCE; }

    private static final Log LOG = new Log("[portal view] ");
    private static MethodHandle rendering, distanceTo;
    private static boolean broken, farBroken;

    /**
     * Performance mode, and Immersive Portals is drawing the world behind a portal right now
     * ({@code PortalRendering.isRendering()}): a portal in that world gets no view of its own, it draws the purple plane.
     * Each such view is one more full pass of the shader pack over the whole screen: in Shane's base, looking into one
     * Nether portal also drew the overworld again through the Nether hub's other portals (2026-11-18; the pack's
     * {@code maxPortalLayer} is 5). Render thread.
     */
    public static boolean nestedNow() {
        if (!performance() || broken) return false;
        try {
            if (rendering == null) {
                Class<?> type = Class.forName("qouteall.imm_ptl.core.render.context_management.PortalRendering");
                rendering = MethodHandles.publicLookup().findStatic(type, "isRendering", MethodType.methodType(boolean.class));
            }
            return (boolean) rendering.invokeExact();
        } catch (Throwable e) {
            broken = true;
            LOG.error("fast", "could not ask Immersive Portals if a portal view is being drawn; portals inside portal views draw as Fully immersive does", e);
            return false;
        }
    }

    /** Performance mode, in the player's own view: {@code portal}'s nearest point is farther than {@link #DISTANCE}. */
    public static boolean farNow(Object portal) {
        if (!performance() || farBroken) return false;
        try {
            if (distanceTo == null) {
                Class<?> type = Class.forName("qouteall.imm_ptl.core.portal.Portal");
                distanceTo = MethodHandles.publicLookup().findVirtual(type, "getDistanceToNearestPointInPortal",
                        MethodType.methodType(double.class, Vec3.class)).asType(MethodType.methodType(double.class, Object.class, Vec3.class));
            }
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            return (double) distanceTo.invokeExact(portal, camera) > DISTANCE.get();
        } catch (Throwable e) {
            farBroken = true;
            LOG.error("far", "could not measure the distance to a portal; every portal in view shows its view", e);
            return false;
        }
    }

    private static final Map<Object, Long> NESTED = new WeakHashMap<>();   // portal -> when it last lost its view inside a view
    private static final long PLANE_NANOS = 250_000_000L;

    /**
     * Immersive Portals decided to draw {@code portal}'s view inside another portal's view and Performance mode took that
     * view away: the portal draws the purple plane from now on, while this keeps happening. Its entity is drawn before this
     * decision in a frame, so the plane follows one frame later.
     */
    public static void lostView(Object portal) { NESTED.put(portal, System.nanoTime()); }

    /**
     * The purple plane for this portal entity: Disabled; in Performance mode a portal farther than the distance (by the
     * distance alone, as Disabled draws by the setting alone: whatever drops the view, Immersive Portals' own range or
     * this mod, the far portal shows the plane), and inside a portal view a portal that lost its view there.
     */
    public static boolean plane(Object portal) {
        if (disabled()) return true;
        if (!performance()) return false;
        if (!nestedNow()) return farNow(portal);
        Long at = NESTED.get(portal);
        return at != null && System.nanoTime() - at < PLANE_NANOS;
    }
}
