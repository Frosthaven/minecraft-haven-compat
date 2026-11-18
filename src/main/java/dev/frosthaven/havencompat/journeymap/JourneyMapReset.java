package dev.frosthaven.havencompat.journeymap;

import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;

/**
 * JourneyMap stops and starts its mapping when the player's dimension changes, and every start runs {@code reset()},
 * which saves all its config files and reads them back ({@code loadConfigProperties}: nine files through Gson, the
 * theme list from disk). On the render thread that was 64 ms of a 110 ms hitch at every portal crossing (2026-11-18;
 * with Immersive Portals a crossing has no loading screen to hide it). The files cannot have changed between two
 * dimensions of one server session, so this fix lets the reload run once per connection and skips it in later resets.
 */
public final class JourneyMapReset {
    private static final Log LOG = new Log("[journeymap reset] ");
    private static boolean inReset;
    private static Object loadedFor;   // the connection whose session already read the config files

    private JourneyMapReset() {}

    public static void resetStarts() { inReset = true; }

    public static void resetEnds() { inReset = false; }

    /** {@code loadConfigProperties()} is about to run: true to skip it. Client thread. */
    public static boolean skipReload() {
        Object connection = Minecraft.getInstance().getConnection();
        if (!inReset || connection == null) return false;
        if (connection != loadedFor) {
            loadedFor = connection;
            return false;
        }
        LOG.once("skip", "JourneyMap's config files are read once per server session, not at every dimension change");
        return true;
    }
}
