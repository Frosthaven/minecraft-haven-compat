package dev.frosthaven.havencompat.journeymap;

import dev.frosthaven.havencompat.Log;
import net.minecraft.resources.ResourceLocation;

/**
 * A waypoint icon whose texture no pack has (another mod's item or texture, renamed or removed by an update) is drawn
 * with JourneyMap's own waypoint icon. JourneyMap reads the missing image as null and crashes the game, both where it
 * lists waypoints (the waypoint manager draws every icon after each save: a player, 2026-11-22, "J, add waypoint,
 * name it, save") and in the waypoint editor's icon button. Unchanged in JourneyMap 6.0.2 to 6.0.10.
 * {@code mixin/JourneyMapIconMixin} and {@code mixin/JourneyMapIconButtonMixin} use it.
 */
public final class WaypointIcons {
    private static final Log LOG = new Log("[waypoint icons] ");
    public static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath("journeymap", "textures/waypoint/icon/waypoint-icon.png");

    private WaypointIcons() {}

    /** logged once per missing icon */
    public static void missing(Object location) {
        LOG.once("missing " + location, "waypoint icon {} has no texture; JourneyMap's own icon is drawn in its place", location);
    }
}
