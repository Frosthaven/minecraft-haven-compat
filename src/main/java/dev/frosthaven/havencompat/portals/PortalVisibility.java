package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.PackDefaults;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjgl.opengl.GL15;

/**
 * Immersive Portals tests each portal in the camera's view cone with an occlusion query (a portal behind a wall is in
 * the cone too) and reads the answer with {@code glGetQueryObjecti(GL_QUERY_RESULT)}, which waits until the GPU has
 * drawn everything queued before it. For a portal that was hidden in the last frame it reads this frame's own query
 * ("no predict", on for the first four tests of every frame and for a portal it mispredicted six times): the render
 * thread then waits for the whole frame so far, in every frame. Measured in Shane's game with Complementary Unbound
 * (2026-11-18, RTX 4090 at 45 to 60 % load): 28 % of the render thread at a base with three portals out of sight
 * (85 fps), 40 % in front of one (45 fps).
 * <p>
 * This fix never reads this frame's query where the last frame's answer exists (Immersive Portals' own prediction mode,
 * for every test), and it reads the last frame's query only when the GPU has it ready; else it uses the portal's last
 * decision, for at most {@value #MAX_MISSES} frames in a row, then it waits once. The cost: a portal that comes into
 * view shows one to three frames late. The first test of a portal still waits, as before.
 * <p>
 * Off switch: {@code "portals": {"exactVisibility": true}} in {@code config/haven-pack-defaults.json}.
 */
public final class PortalVisibility {
    private static final Log LOG = new Log("[portal visibility] ");
    private static final int GL_QUERY_RESULT_AVAILABLE = 0x8867;
    private static final int MAX_MISSES = 2;

    private record State(boolean decision, int misses) {}

    private static Boolean enabled;
    // Render thread only.
    private static final Map<Object, State> LAST = new WeakHashMap<>();   // per PortalRenderInfo.Visibility
    private static Object current;     // the Visibility of the test in progress
    private static Object thisFrame;   // its query of this frame
    private static boolean missed;

    private PortalVisibility() {}

    public static boolean enabled() {
        if (enabled == null) {
            boolean exact = false;
            try {
                var portals = PackDefaults.section(PackDefaults.file(), "portals");
                if (portals != null && portals.has("exactVisibility")) exact = portals.get("exactVisibility").getAsBoolean();
            } catch (Exception e) {
                LOG.warnOnce("config", "could not read {} ({}); the fix stays on", PackDefaults.FILE, e.toString());
            }
            enabled = !exact;
            if (exact) LOG.once("off", "off (portals.exactVisibility = true in {})", PackDefaults.FILE);
            else LOG.once("on", "portal visibility tests no longer wait for the GPU (a portal coming into view can show a few frames late)");
        }
        return enabled;
    }

    /** {@code Visibility.acquireThisFrameQuery()} returned: a visibility test of that entry starts. */
    public static void begin(Object visibility, Object query) {
        current = visibility;
        thisFrame = query;
        missed = false;
    }

    /**
     * {@code GlQueryObject.fetchQueryResult()} is about to wait for query {@code id}.
     *
     * @return the answer to use, or null to read the query as Immersive Portals does
     */
    public static Boolean answer(Object query, int id) {
        if (current == null || query == thisFrame) return null;
        State last = LAST.get(current);
        if (last == null || last.misses >= MAX_MISSES) return null;
        if (GL15.glGetQueryObjecti(id, GL_QUERY_RESULT_AVAILABLE) != 0) return null;
        missed = true;
        return last.decision;
    }

    /** {@code PortalRenderInfo.renderAndDecideVisibility} returns {@code decision}. */
    public static void end(boolean decision) {
        if (current == null) return;
        State last = LAST.get(current);
        LAST.put(current, new State(decision, missed && last != null ? last.misses + 1 : 0));
        current = null;
        thisFrame = null;
    }
}
