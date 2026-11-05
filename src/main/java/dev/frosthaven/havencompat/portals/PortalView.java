package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.settings.SavedChoice;

/**
 * The player's Immersive Portal Rendering setting (Experimental Optimizations), saved on this computer. Normal: Immersive
 * Portals as configured. Disabled: no view through a portal, {@link PortalPlane} draws a purple plane; teleporting stays.
 */
public final class PortalView {
    public enum Level { NORMAL, DISABLED }

    public static final SavedChoice<Level> SETTING = new SavedChoice<>(Level.class, Level.NORMAL,
            "haven-compat-portal-view.json", "portalView", "Immersive Portal Rendering", new Log("[portal view] "));

    private PortalView() {}

    public static boolean disabled() { return SETTING.get() == Level.DISABLED; }
}
