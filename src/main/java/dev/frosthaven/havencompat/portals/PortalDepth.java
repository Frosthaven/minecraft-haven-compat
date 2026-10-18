package dev.frosthaven.havencompat.portals;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.frosthaven.havencompat.Log;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Immersive Portals' shader path (Iris shaders on) on a driver without {@code glCopyImageSubData} (OpenGL 4.3 or
 * ARB_copy_image; macOS stops at 4.1). Two things go wrong there, seen 2026-10-17 on an M3 Pro:
 * <ol>
 * <li>{@code IrisPortalRenderer} blits depth from the main framebuffer into its portal buffers (GL 3.0) after turning
 *     the main framebuffer's stencil off. The portal buffers are packed depth+stencil; a plain depth buffer on Apple's
 *     driver is 32-bit float. The spec makes a depth blit between different formats an error (PC drivers let it pass,
 *     Apple's does not: "Depth blit ... failed with GL error 0x502"), and Immersive Portals answers the error by
 *     switching itself to its "compatibility" renderer.</li>
 * <li>{@code IrisCompatibilityPortalRenderer} copies depth+stencil and color with {@code GL43C.glCopyImageSubData}
 *     ({@code IPIrisHelper}), no capability check (upstream issue 1792): a missing function, so LWJGL aborts the JVM.</li>
 * </ol>
 * The two mixins fix both where the call is missing, and only there, so a machine with OpenGL 4.3 runs Immersive
 * Portals unchanged: {@link #keepMainStencil} keeps the main framebuffer's stencil on (both framebuffers then get the
 * same packed format from Immersive Portals' own texImage2D hook, the blit is legal and the normal renderer stays),
 * and {@link #blitDepthStencil} / {@link #blitColor} stand in for the 4.3 copy. The test flag {@value #ASSUME_FLAG}
 * takes this path on a machine that has the call.
 */
public final class PortalDepth {
    public static final String STENCIL_TARGET = "qouteall.imm_ptl.core.compat.IPPortingLibCompat";
    public static final String COPY_TARGET = "qouteall.imm_ptl.core.compat.iris_compatibility.IPIrisHelper";
    /** JVM flag: {@code -Dhaven_compat.assumeNoCopyImage=true} runs the fallback path on a driver that has the call. */
    public static final String ASSUME_FLAG = "haven_compat.assumeNoCopyImage";
    private static final Log LOG = new Log("[portal depth] ");
    private static final Set<String> PATCHED = ConcurrentHashMap.newKeySet();
    private static volatile Boolean noCopyImage;   // decided once, on a thread with a GL context

    private PortalDepth() {}

    /** True on a driver without glCopyImageSubData, or with the test flag. False (not kept) off the render thread. */
    public static boolean noCopyImage() {
        Boolean v = noCopyImage;
        if (v == null) {
            if (Boolean.getBoolean(ASSUME_FLAG)) {
                v = true;
                LOG.once("caps", "{} set: taking the no-glCopyImageSubData path on purpose", ASSUME_FLAG);
            } else {
                try {
                    v = GL.getCapabilities().glCopyImageSubData == 0L;
                } catch (IllegalStateException noContext) {
                    return false;
                }
                LOG.once("caps", "glCopyImageSubData {}: Immersive Portals' shader path {}",
                        v ? "missing (no OpenGL 4.3 or ARB_copy_image)" : "available",
                        v ? "gets the stencil hold and the blit copies" : "runs unchanged");
            }
            noCopyImage = v;
        }
        return v;
    }

    /** The mixin plugin: this Immersive Portals class got its patch. */
    public static void patched(String targetClass) { PATCHED.add(targetClass); }

    /** Both patches are in place (the mixins applied to both Immersive Portals classes). */
    public static boolean patched() { return PATCHED.contains(STENCIL_TARGET) && PATCHED.contains(COPY_TARGET); }

    /**
     * {@code IPPortingLibCompat.setIsStencilEnabled(target, enabled)}: where the call is missing, the main framebuffer
     * keeps its stencil. Immersive Portals' own hook then gives it the same packed depth+stencil format as the portal
     * buffers, so the depth blit between them is legal and the normal shader renderer stays in use.
     */
    public static boolean keepMainStencil(RenderTarget target, boolean enabled) {
        if (enabled || !noCopyImage() || target != Minecraft.getInstance().getMainRenderTarget()) return enabled;
        LOG.once("hold", "the main framebuffer keeps its stencil in Immersive Portals' shader path: its depth format"
                + " then matches the portal buffers' and the depth blit is legal");
        return true;
    }

    /** Stands in for {@code IPIrisHelper.newCopyDepthStencil}: depth (and stencil, when both sides have one) by blit. */
    public static void blitDepthStencil(RenderTarget from, RenderTarget to) {
        blit(from, to, GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, "depth+stencil");
    }

    /** Stands in for {@code IPIrisHelper.copyColor}: color attachment 0 by blit. */
    public static void blitColor(RenderTarget from, RenderTarget to) {
        blit(from, to, GL11.GL_COLOR_BUFFER_BIT, "color");
    }

    private static void blit(RenderTarget from, RenderTarget to, int mask, String what) {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        if ((mask & GL11.GL_STENCIL_BUFFER_BIT) != 0
                && !(hasStencil(GL30.GL_READ_FRAMEBUFFER) && hasStencil(GL30.GL_DRAW_FRAMEBUFFER))) {
            mask &= ~GL11.GL_STENCIL_BUFFER_BIT;   // a stencil blit needs one on both sides
        }
        for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) { /* earlier code's errors are not this blit's */ }
        GL30.glBlitFramebuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, mask, GL11.GL_NEAREST);
        int err = GL11.glGetError();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        if (err == GL11.GL_NO_ERROR) {
            LOG.once("blit:" + what, "{} copied by blit in place of glCopyImageSubData", what);
        } else {
            LOG.warnOnce("blit:" + what + ":" + err, "{} blit failed with GL error 0x{} (depth formats: from 0x{}, to 0x{})",
                    what, Integer.toHexString(err), Integer.toHexString(depthFormat(from)), Integer.toHexString(depthFormat(to)));
        }
    }

    private static boolean hasStencil(int framebufferTarget) {
        return GL30.glGetFramebufferAttachmentParameteri(framebufferTarget, GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_NONE;
    }

    /** The depth texture's internal format (0 without one), for the log; leaves the texture binding as it was. */
    static int depthFormat(RenderTarget target) {
        int tex = target.getDepthTextureId();
        if (tex <= 0) return 0;
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        return format;
    }
}
