package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.settings.SavedChoice;

/**
 * The player's Immersive Portal Rendering setting (Experimental Optimizations), saved on this computer. Normal: Immersive
 * Portals as configured. Fast: the view through a portal, but a portal seen through another portal draws the purple plane ({@link #nestedNow}). Disabled: no view through a portal, {@link PortalPlane} draws a purple plane; teleporting stays.
 */
public final class PortalView {
    public enum Level { NORMAL, FAST, DISABLED }

    public static final SavedChoice<Level> SETTING = new SavedChoice<>(Level.class, Level.FAST,
            "haven-compat-portal-view.json", "portalView", "Immersive Portal Rendering", new Log("[portal view] "));

    private PortalView() {}

    public static boolean disabled() { return SETTING.get() == Level.DISABLED; }

    private static final Log LOG = new Log("[portal view] ");
    private static java.lang.invoke.MethodHandle rendering;
    private static boolean broken;

    /**
     * Fast, and Immersive Portals is drawing the world behind a portal right now ({@code PortalRendering.isRendering()}):
     * a portal in that world gets no view of its own, it draws the purple plane. Each such view is one more full pass
     * of the shader pack over the whole screen: in Shane's base, looking into one Nether portal also drew the overworld
     * again through the Nether hub's other portals (2026-11-18; the pack's {@code maxPortalLayer} is 5). Render thread.
     */
    public static boolean nestedNow() {
        if (SETTING.get() != Level.FAST || broken) return false;
        try {
            if (rendering == null) {
                Class<?> type = Class.forName("qouteall.imm_ptl.core.render.context_management.PortalRendering");
                rendering = java.lang.invoke.MethodHandles.publicLookup().findStatic(type, "isRendering", java.lang.invoke.MethodType.methodType(boolean.class));
            }
            return (boolean) rendering.invokeExact();
        } catch (Throwable e) {
            broken = true;
            LOG.error("fast", "could not ask Immersive Portals if a portal view is being drawn; Fast draws portals as Normal does", e);
            return false;
        }
    }

    private static final java.util.Map<Object, Long> NESTED = new java.util.WeakHashMap<>();   // portal -> when it last lost its view
    private static final long PLANE_NANOS = 250_000_000L;

    /**
     * Immersive Portals decided to draw {@code portal}'s view inside another portal's view and Fast took that view
     * away: the portal draws the purple plane from now on, while this keeps happening. Its entity is drawn before this
     * decision in a frame, so the plane follows one frame later.
     */
    public static void lostView(Object portal) { NESTED.put(portal, System.nanoTime()); }

    /** The purple plane for this portal entity: Disabled, or Fast inside a portal view for a portal that lost its view. */
    public static boolean plane(Object portal) {
        if (disabled()) return true;
        if (!nestedNow()) return false;
        Long at = NESTED.get(portal);
        return at != null && System.nanoTime() - at < PLANE_NANOS;
    }
}
