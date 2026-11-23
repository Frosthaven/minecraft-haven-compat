package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The far side of a portal is ready before the player gets there. Immersive Portals draws the far side through the
 * portal, so the game builds the chunk meshes of what the portal's opening shows and nothing else; the rest of the area
 * is built the moment the player arrives, all at once: seconds of low frame rate at the first arrival through a portal
 * (25 slow frames, 6 over 100 ms, 2026-11-22; later arrivals are smooth because the meshes exist).
 * <p>
 * While the player is within {@value #RANGE} blocks of a portal, after every {@value #EVERY}th drawing of its view one
 * more terrain setup runs with a frustum that looks another way from the same place (four sides, up, down,
 * {@value #ROUNDS} rounds): the mesh builder sees those sections as wanted and builds them on its worker threads. The
 * setup runs after the picture is done and nothing is drawn from it. (A first version swapped the frustum of the
 * drawing itself: on those frames the portal's view lost what it should show and flickered.) A portal is done after its
 * rounds and is looked at again after {@value #AGAIN_MS} ms out of range.
 */
public final class PortalLookAround {
    private static final Log LOG = new Log("[portal look-around] ");
    private static final double RANGE = 96;
    private static final int EVERY = 4, ROUNDS = 3;
    private static final long AGAIN_MS = 60_000;
    private static final class State { int draws, step; long seen; }
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static Method isRendering, renderingPortal, layer, distance;
    private static boolean broken;

    private PortalLookAround() {}

    /**
     * After a portal view has been drawn: the frustum of an extra terrain setup that looks another way, or null when
     * none is due. The caller runs that setup after the picture is done, so it only tells the mesh builder what is
     * wanted; the next drawing sets up the real view again.
     */
    public static Frustum next() {
        if (broken || Boolean.getBoolean("haven_compat.noLookAround")) return null;
        try {
            if (isRendering == null) {
                Class<?> rendering = Class.forName("qouteall.imm_ptl.core.render.context_management.PortalRendering");
                isRendering = rendering.getMethod("isRendering");
                renderingPortal = rendering.getMethod("getRenderingPortal");
                layer = rendering.getMethod("getPortalLayer");
                distance = Class.forName("qouteall.imm_ptl.core.portal.Portal").getMethod("getDistanceToNearestPointInPortal", Vec3.class);
            }
            if (!(boolean) isRendering.invoke(null) || (int) layer.invoke(null) != 1) return null;
            Minecraft mc = Minecraft.getInstance();
            Object portal = renderingPortal.invoke(null);
            if (mc.player == null || !(portal instanceof Entity entity)) return null;
            if ((double) distance.invoke(portal, mc.player.position()) > RANGE) return null;
            long now = System.currentTimeMillis();
            State state = STATES.get(entity.getUUID());
            if (state == null || now - state.seen > AGAIN_MS) {
                STATES.values().removeIf(s -> now - s.seen > AGAIN_MS);
                STATES.put(entity.getUUID(), state = new State());
                LOG.once("first", "near a portal, the far side is looked around for after each drawing, so its chunk meshes are built before the player arrives");
            }
            state.seen = now;
            if (state.step >= 6 * ROUNDS || ++state.draws % EVERY != 0) return null;
            int way = state.step++ % 6;
            Matrix4f view = new Matrix4f();
            if (way < 4) view.rotateY((float) (way * Math.PI / 2));
            else view.rotateX((float) (way == 4 ? Math.PI / 2 : -Math.PI / 2));
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(100), 1f, 0.05f, mc.options.getEffectiveRenderDistance() * 16f * 4f);
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();   // the far side's camera while a portal view is drawn
            Frustum look = new Frustum(view, projection);
            look.prepare(camera.x, camera.y, camera.z);
            return look;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            LOG.error("look", "could not look around on a portal's far side; its meshes are built on arrival as before", e);
            return null;
        }
    }
}
