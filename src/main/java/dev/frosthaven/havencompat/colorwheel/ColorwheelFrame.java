package dev.frosthaven.havencompat.colorwheel;

import dev.djefrey.colorwheel.engine.uniform.ClrwlUniforms;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.frosthaven.havencompat.Log;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Colorwheel keeps its per-frame camera data (render origin, camera position, view and projection, frustum planes) in
 * uniform buffers that all its engines share, and writes them once per engine per frame, at the engine's first draw.
 * With Immersive Portals one frame draws several views: the player's, each portal view, a carriage's far half, each
 * with its own level, render origin and camera. A later draw of the first view (its translucent pass, or its solid pass
 * when a portal view ran in between) then read another view's data: Create parts were culled by the wrong frustum or
 * drawn at another level's render origin. Seen as a train carriage whose body, bogey or both vanish near portals and
 * controls drawn high in the sky, only with a shader pack on (2026-11-22). Immersive Portals repairs the same for plain
 * Flywheel ({@code MixinFlywheelRenderDispatcher_PortalPassSkip}: "main-view frame uniforms restored before each main
 * draw"); this is that repair for Colorwheel, for every view.
 * <p>
 * {@link #wroteFrame} and {@link #wrotePass} note which view the buffers hold; {@link #frameStale} and
 * {@link #passStale} tell {@code mixin/ColorwheelFrameMixin} to write them again before a draw of another view.
 */
public final class ColorwheelFrame {
    private static final Log LOG = new Log("[colorwheel frame] ");
    private static Object frameLevel, passLevel;
    private static Vec3 framePos, passPos;
    private static final Vector3f FRAME_LOOK = new Vector3f(), PASS_LOOK = new Vector3f();
    private static final Matrix4f PASS_PROJECTION = new Matrix4f();

    private ColorwheelFrame() {}

    public static void wroteFrame(RenderContext c) {
        frameLevel = c.level();
        framePos = c.camera().getPosition();
        FRAME_LOOK.set(c.camera().getLookVector());
    }

    public static void wrotePass(RenderContext c) {
        passLevel = c.level();
        passPos = c.camera().getPosition();
        PASS_LOOK.set(c.camera().getLookVector());
        PASS_PROJECTION.set(c.projection());
    }

    public static boolean frameStale(RenderContext c) {
        return frameLevel != c.level() || !c.camera().getPosition().equals(framePos) || !FRAME_LOOK.equals(c.camera().getLookVector());
    }

    public static boolean passStale(RenderContext c) {
        return passLevel != c.level() || !c.camera().getPosition().equals(passPos) || !PASS_LOOK.equals(c.camera().getLookVector())
                || !PASS_PROJECTION.equals(c.projection());
    }

    /** Before an engine's draw: its frame data again when the buffers hold another view's. */
    public static void restoreFrame(RenderContext c) {
        if (!frameStale(c)) return;
        ClrwlUniforms.updateFrame(c);
        LOG.once("frame", "Colorwheel's camera data is written again before a draw when another view (a portal's) wrote it last");
    }
}
