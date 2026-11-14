package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.PackDefaults;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/**
 * Builds Iris's shader pipelines for the other dimensions the moment Iris builds the one the player is in, so the
 * first look through a portal does not do it.
 * <p>
 * Iris keeps one pipeline per dimension and builds it on first use ({@code PipelineManager.preparePipeline}). With
 * Immersive Portals that first use was the first frame a portal into that dimension was in view: seconds of shader
 * linking on the render thread (4 s for the Nether and 6 s for the End with Complementary on an M1 Pro), then a
 * full chunk rebuild. Once per dimension per session. {@code mixin/IrisPipelinePrewarmMixin} calls {@link #created}
 * at the tail of {@code preparePipeline} whenever it created a new pipeline; this then builds the other two of
 * overworld, the_nether and the_end right there and makes the player's current again (an id that exists is a map
 * lookup, no rebuild). That moment is the first frame of the world, behind the "Loading terrain" screen, so the
 * join takes those seconds longer and play does not hitch.
 * <p>
 * Timing detail: Immersive Portals switches to its Iris renderer on the first world frame and that switch destroys
 * every pipeline; pipelines built before it (at the login's own build) would be wasted. So with Immersive Portals
 * present nothing is built until {@code IPCGlobal.renderer} is its Iris renderer. A shader pack reload destroys the
 * pipelines too; the next build after it triggers this again. Shaders off (a vanilla pipeline) and the title screen's
 * build (no level) do nothing.
 * <p>
 * Portal views: Immersive Portals 2.8 keeps up to three more pipeline maps ({@code IPIrisPortalViewPipelines.PIPELINES})
 * and swaps one into the manager while it renders a second, third or fourth view of a dimension in one frame (the
 * player's own dimension seen back through a far portal is the first such view). It builds those on first look too:
 * about 0.4 s each on the render thread, in play. Two rules follow. A pipeline created into one of those maps starts
 * no prewarm (0.31.1 and before did: every first view also built the two other dimensions for that view, 3.2 s for
 * two views). And the prewarm builds the first {@code portals.prewarmPortalViews} (default 2) views of the player's
 * dimension behind the loading screen, the same way Immersive Portals does: its map in the manager, then its
 * {@code onCreated}. Other views stay on first look (each pipeline holds full-size render targets, so not all nine).
 * Off switch: {@code "portals": {"prewarmPipelines": false}} in
 * {@code config/haven-pack-defaults.json}. Reflection only; any failure is logged once and the game goes on as before.
 */
public final class PipelinePrewarm {
    public static final String TARGET = "net.irisshaders.iris.pipeline.PipelineManager";
    private static final Log LOG = new Log("[iris prewarm] ");
    private static final String IP_GLOBAL = "qouteall.imm_ptl.core.IPCGlobal";
    static final List<String[]> DIMENSIONS = List.of(
            new String[] {"minecraft", "overworld"}, new String[] {"minecraft", "the_nether"}, new String[] {"minecraft", "the_end"});
    private static final String IP_VIEWS = "qouteall.imm_ptl.core.compat.iris_integration.IPIrisPortalViewPipelines";
    private static final int DEFAULT_VIEWS = 2;
    private static boolean active, broken;
    private static Boolean enabled;
    private static int round;

    private PipelinePrewarm() {}

    /** The manager's pipeline map, by the mixin. */
    public interface Pipelines {
        Map<?, ?> haven_compat$pipelines();

        void haven_compat$pipelines(Map<?, ?> map);
    }

    /** True while this class itself is calling preparePipeline: the mixin stays out of those calls. */
    public static boolean active() {
        return active;
    }

    /** From the mixin: {@code manager.preparePipeline(createdId)} has just created {@code pipeline}. */
    public static void created(Object manager, Object createdId, Object pipeline) {
        if (active || broken) return;
        try {
            if (!enabled()) {
                LOG.once("off", "off (portals.prewarmPipelines = false in {})", PackDefaults.FILE);
                return;
            }
            if (pipeline == null || pipeline.getClass().getSimpleName().startsWith("Vanilla")) return;   // shaders off
            if (Minecraft.getInstance().level == null) return;   // the title screen's build; a join rebuilds anyway
            List<Map<?, ?>> views = portalViews();
            Map<?, ?> own = ((Pipelines) manager).haven_compat$pipelines();
            for (Map<?, ?> view : views) if (view == own) return;   // Immersive Portals built a portal view: not a new session of pipelines
            if (!portalRendererReady()) {
                LOG.once("ip-switch", "waiting for Immersive Portals' switch to its Iris renderer (it rebuilds every pipeline); the other dimensions are built after it");
                return;
            }
            Class<?> idType = createdId.getClass();
            Method prepare = manager.getClass().getMethod("preparePipeline", idType);
            Constructor<?> id = idType.getConstructor(String.class, String.class);
            String namespace = (String) idType.getMethod("getNamespace").invoke(createdId);
            String name = (String) idType.getMethod("getName").invoke(createdId);
            round++;
            active = true;
            boolean fresh = false;
            try {
                for (String[] dim : DIMENSIONS) {
                    if (dim[0].equals(namespace) && dim[1].equals(name)) continue;
                    long t = System.nanoTime();
                    Object other = id.newInstance(dim[0], dim[1]);
                    if (own.containsKey(other)) continue;
                    fresh = true;
                    prepare.invoke(manager, other);
                    LOG.once("warm:" + round + ":" + dim[1], "shader pipeline for {}:{} built behind the loading screen ({} ms)",
                            dim[0], dim[1], (System.nanoTime() - t) / 1_000_000);
                }
                // Portal views only with a new set of pipelines (a join, a shader reload): a single late build (a
                // modded dimension's first look) happens in play, and more builds there would be a longer stall.
                int count = fresh ? Math.min(views.size(), viewCount()) : 0;
                for (int i = 0; i < count; i++) {
                    if (views.get(i).containsKey(createdId)) continue;
                    long t = System.nanoTime();
                    ((Pipelines) manager).haven_compat$pipelines(views.get(i));
                    Object made;
                    try {
                        made = prepare.invoke(manager, createdId);
                    } finally {
                        ((Pipelines) manager).haven_compat$pipelines(own);
                    }
                    for (Method m : Class.forName(IP_VIEWS).getMethods()) {
                        if (m.getName().equals("onCreated")) m.invoke(null, createdId, made);
                    }
                    LOG.once("view:" + round + ":" + i, "shader pipeline for portal view {} of {}:{} built behind the loading screen ({} ms)",
                            i + 1, namespace, name, (System.nanoTime() - t) / 1_000_000);
                }
                prepare.invoke(manager, createdId);   // the player's stays current: an id that exists is a lookup
            } finally {
                active = false;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            LOG.error("prewarm", "could not pre-build Iris's pipelines; portals build them on first look as before", e);
        }
    }

    /** Without Immersive Portals: yes. With it: only once its renderer is the Iris one (after its pipeline-destroying switch). */
    private static boolean portalRendererReady() throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("immersive_portals_core")) return true;
        Object renderer = Class.forName(IP_GLOBAL).getField("renderer").get(null);
        return renderer != null && renderer.getClass().getName().contains("iris_compatibility");
    }

    /** Immersive Portals' portal-view pipeline maps; empty without it or before its 2.8. */
    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> portalViews() throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("immersive_portals_core")) return List.of();
        try {
            return (List<Map<?, ?>>) Class.forName(IP_VIEWS).getField("PIPELINES").get(null);
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            return List.of();
        }
    }

    private static int viewCount() {
        try {
            var portals = PackDefaults.section(PackDefaults.file(), "portals");
            if (portals != null && portals.has("prewarmPortalViews")) return Math.max(0, portals.get("prewarmPortalViews").getAsInt());
        } catch (Exception e) {
            LOG.warnOnce("config-views", "could not read {} ({}); {} portal views are prebuilt", PackDefaults.FILE, e.toString(), DEFAULT_VIEWS);
        }
        return DEFAULT_VIEWS;
    }

    private static boolean enabled() {
        if (enabled == null) {
            boolean on = true;
            try {
                var portals = PackDefaults.section(PackDefaults.file(), "portals");
                if (portals != null && portals.has("prewarmPipelines")) on = portals.get("prewarmPipelines").getAsBoolean();
            } catch (Exception e) {
                LOG.warnOnce("config", "could not read {} ({}); prewarm stays on", PackDefaults.FILE, e.toString());
            }
            enabled = on;
        }
        return enabled;
    }
}
