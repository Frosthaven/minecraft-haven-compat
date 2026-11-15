package dev.frosthaven.havencompat.flywheel;

import dev.frosthaven.havencompat.Log;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Flywheel's instancing backend (and Colorwheel's, which uses the same class) attaches its two light buffers to buffer
 * textures at the start of every pass ({@code InstancedLight.bind} -> {@code TextureBuffer.bind}: {@code glTexBuffer}).
 * It fills those buffers only when a visual asked for Flywheel's own light data. On a driver without Direct State
 * Access (macOS, OpenGL 4.1) an unfilled buffer is a name from {@code glGenBuffers} that was never bound, which is not
 * a buffer object yet, and {@code glTexBuffer} with it is a GL_INVALID_OPERATION: one per pass, in every world with
 * Create parts. Nothing draws wrong, but Immersive Portals reads the error flag whenever it draws a portal and logs
 * "OpenGL Error1282" with a stack trace, every frame with a portal in view (4,182 in 2 minutes).
 * <p>
 * Here a buffer that does not exist yet is bound once before the attach, which creates it (empty). With Direct State
 * Access ({@code glCreateBuffers}) the buffer always exists and nothing is done.
 */
public final class LightBufferFix {
    private static final Log LOG = new Log("[flywheel light] ");
    private static Boolean needed;

    private LightBufferFix() {}

    /** Before {@code TextureBuffer.bind(buffer)}: the buffer name is a buffer object. */
    public static void ensureExists(int buffer) {
        if (needed == null) {
            GLCapabilities caps = GL.getCapabilities();
            needed = !(caps.OpenGL45 || caps.GL_ARB_direct_state_access);
        }
        if (!needed || buffer == 0 || GL15.glIsBuffer(buffer)) return;
        int bound = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bound);
        LOG.once("made", "no Direct State Access on this OpenGL context: Flywheel's unfilled light buffers are created before they go to a buffer texture");
    }
}
