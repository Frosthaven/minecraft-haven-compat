package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.settings.SavedChoice;

/**
 * The player's Immersive Portal Rendering setting (Experimental Optimizations), saved on this computer. Normal: Immersive
 * Portals as configured. Disabled: no view through a portal, {@link PortalPlane} draws a purple plane; teleporting stays.
 */
public final class PortalView {
    public enum Level { NORMAL, FAST, DISABLED }

    public static final SavedChoice<Level> SETTING = new SavedChoice<>(Level.class, Level.NORMAL,
            "haven-compat-portal-view.json", "portalView", "Immersive Portal Rendering", new Log("[portal view] "));

    private PortalView() {}

    public static boolean disabled() { return SETTING.get() == Level.DISABLED; }

    private static final Log LOG = new Log("[portal view] ");
    private static java.lang.invoke.MethodHandle rendering;
    private static boolean broken;

    /**
     * Fast, and Immersive Portals is drawing the world behind a portal right now ({@code PortalRendering.isRendering()}):
     * the cheaper drawing applies (see {@code mixin/PortalFastShadowMixin}). Render thread.
     */
    public static boolean fastSunlessNow() {
        if (!fastNow()) return false;
        var level = net.minecraft.client.Minecraft.getInstance().level;   // Immersive Portals has switched it to the world behind the portal
        return level != null && level.dimensionType().hasCeiling() && !level.dimensionType().hasSkyLight();
    }

    /**
     * Fast, and the world being drawn behind a portal has no sun (a ceiling and no sky light: the Nether). Only there
     * the shadow pass may be left empty: in a world with a sun an empty shadow map lights every room like open ground.
     */
    public static boolean fastNow() {
        if (SETTING.get() != Level.FAST || broken) return false;
        try {
            if (rendering == null) {
                Class<?> type = Class.forName("qouteall.imm_ptl.core.render.context_management.PortalRendering");
                rendering = java.lang.invoke.MethodHandles.publicLookup().findStatic(type, "isRendering", java.lang.invoke.MethodType.methodType(boolean.class));
            }
            return (boolean) rendering.invokeExact();
        } catch (Throwable e) {
            broken = true;
            LOG.error("fast", "could not ask Immersive Portals if a portal view is being drawn; Fast draws portal views as Normal does", e);
            return false;
        }
    }
}
