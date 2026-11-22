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
    private static final int DEFAULT_VIEWS = 3;
    private static boolean active, broken;
    /** Dimension pairs a portal links: each one's engine also draws with the other's pipelines (a carriage's far half). */
    public static final java.util.Set<String> LINKS = new java.util.concurrent.ConcurrentSkipListSet<>(List.of(
            "minecraft:overworld|minecraft:the_nether", "minecraft:overworld|minecraft:the_end"));

    public static boolean linked(String a, String b) {
        return LINKS.contains(a + "|" + b) || LINKS.contains(b + "|" + a);
    }
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
            boolean fresh = false;
            for (String[] dim : DIMENSIONS) if (!own.containsKey(id.newInstance(dim[0], dim[1]))) fresh = true;
            // Only a new set of pipelines (a join, a shader reload) starts a round: a single late build (a modded
            // dimension's first look) is handled by PortalWarm when a portal to it loads.
            if (!fresh) return;
            round++;
            int thisRound = round;
            PrewarmScreen.roundStarted();
            WarmQueue.clear();
            Session session = new Session(manager, prepare, id, idType, views);
            current = session;
            for (String[] dim : DIMENSIONS) {
                if (dim[0].equals(namespace) && dim[1].equals(name)) continue;
                WarmQueue.add(() -> session.main(dim, thisRound));
            }
            int count = Math.min(views.size(), viewCount());
            List<String[]> viewed = new java.util.ArrayList<>(DIMENSIONS);
            if (DIMENSIONS.stream().noneMatch(d -> d[0].equals(namespace) && d[1].equals(name))) viewed.add(new String[] {namespace, name});
            for (String[] dim : viewed) for (int i = 0; i < count; i++) {
                int view = i;
                WarmQueue.add(() -> session.view(dim, view, thisRound));
            }
            if (ModList.get().isLoaded("colorwheel")) {
                WarmQueue.add(() -> session.guard(() -> warmColorwheel(own, id, idType)));
                int pairs = 0;   // an engine's steps: its own pipelines and each linked dimension's (main and views)
                for (String[] dim : DIMENSIONS) {
                    int links = 0;
                    for (String[] other : DIMENSIONS) if (other != dim && linked(dim[0] + ":" + dim[1], other[0] + ":" + other[1])) links++;
                    pairs += (1 + count) * (1 + links);
                }
                dev.frosthaven.havencompat.colorwheel.ColorwheelWarm.queueEngines(pairs + 1, PrewarmScreen::done);
            } else {
                WarmQueue.add(PrewarmScreen::done);
            }
            // links found earlier in the session (a shader reload destroyed their pipelines too)
            for (String link : LINKS) {
                if (VANILLA_LINKS.contains(link)) continue;
                String[] ends = link.split("\\|");
                link(ends[0], ends[1]);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            PrewarmScreen.done();
            LOG.error("prewarm", "could not pre-build Iris's pipelines; portals build them on first look as before", e);
        }
    }

    /** The round's tools: Iris's manager and how to ask it for a pipeline. Kept for PortalWarm's late builds. */
    static final class Session {
        final Object manager;
        final Method prepare;
        final Constructor<?> id;
        final Class<?> idType;
        final List<Map<?, ?>> views;

        Session(Object manager, Method prepare, Constructor<?> id, Class<?> idType, List<Map<?, ?>> views) {
            this.manager = manager; this.prepare = prepare; this.id = id; this.idType = idType; this.views = views;
        }

        interface Work { void run() throws ReflectiveOperationException; }

        /** One step's work with the mixin kept out of our own preparePipeline calls; a failure ends the prewarm. */
        void guard(Work work) {
            if (broken) return;
            active = true;
            try {
                work.run();
                restore();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                broken = true;
                WarmQueue.clear();
                PrewarmScreen.done();
                LOG.error("prewarm", "could not pre-build Iris's pipelines; portals build them on first look as before", e);
            } finally {
                active = false;
            }
        }

        /** The player's dimension stays the current pipeline: an id that exists is a lookup. */
        private void restore() throws ReflectiveOperationException {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            var at = level.dimension().location();
            prepare.invoke(manager, id.newInstance(at.getNamespace(), at.getPath()));
        }

        void main(String[] dim, int round) {
            guard(() -> {
                Object other = id.newInstance(dim[0], dim[1]);
                if (((Pipelines) manager).haven_compat$pipelines().containsKey(other)) return;
                long t = System.nanoTime();
                prepare.invoke(manager, other);
                LOG.once("warm:" + round + ":" + dim[1], "shader pipeline for {}:{} built behind the loading screen ({} ms)",
                        dim[0], dim[1], (System.nanoTime() - t) / 1_000_000);
            });
        }

        void view(String[] dim, int i, int round) {
            guard(() -> {
                Object viewed = id.newInstance(dim[0], dim[1]);
                Map<?, ?> own = ((Pipelines) manager).haven_compat$pipelines();
                if (!own.containsKey(viewed) || views.get(i).containsKey(viewed)) return;
                long t = System.nanoTime();
                ((Pipelines) manager).haven_compat$pipelines(views.get(i));
                Object made;
                try {
                    made = prepare.invoke(manager, viewed);
                } finally {
                    ((Pipelines) manager).haven_compat$pipelines(own);
                }
                for (Method m : Class.forName(IP_VIEWS).getMethods()) {
                    if (m.getName().equals("onCreated")) m.invoke(null, viewed, made);
                }
                LOG.once("view:" + round + ":" + dim[1] + ":" + i, "shader pipeline for portal view {} of {}:{} built ({} ms)",
                        i + 1, dim[0], dim[1], (System.nanoTime() - t) / 1_000_000);
            });
        }
    }

    static Session current;
    private static final java.util.Set<String> VANILLA_LINKS = java.util.Set.copyOf(LINKS);

    /**
     * Queues everything a portal between these two dimensions ("namespace:path") needs that does not exist yet: each
     * side's pipeline and portal views, its client level and Colorwheel engine, and each engine's programs for both
     * sides' pipelines. Steps that find their work done cost nothing.
     */
    public static void link(String from, String to) {
        Session session = current;
        if (session == null || broken) return;
        int count = Math.min(session.views.size(), viewCount());
        for (String side : List.of(from, to)) {
            String[] dim = side.split(":", 2);
            WarmQueue.add(() -> session.main(dim, round));
            for (int i = 0; i < count; i++) {
                int view = i;
                WarmQueue.add(() -> session.view(dim, view, round));
            }
        }
        if (ModList.get().isLoaded("colorwheel")) dev.frosthaven.havencompat.colorwheel.ColorwheelWarm.queueLink(from, to);
    }


    /**
     * Colorwheel's rewritten shader sources for the three dimensions, behind the same loading screen (see
     * {@code colorwheel/ColorwheelWarm}): else it rewrites them when a portal first comes into view.
     */
    private static void warmColorwheel(Map<?, ?> pipelines, Constructor<?> id, Class<?> idType) throws ReflectiveOperationException {
        Object pack = ((java.util.Optional<?>) Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPack").invoke(null)).orElse(null);
        if (pack == null) return;
        Method programSet = pack.getClass().getMethod("getProgramSet", idType);
        for (String[] dim : DIMENSIONS) {
            Object dimension = id.newInstance(dim[0], dim[1]);
            Object pipeline = pipelines.get(dimension);
            if (pipeline == null || !pipeline.getClass().getSimpleName().equals("IrisRenderingPipeline")) continue;
            long t = System.nanoTime();
            int sources = dev.frosthaven.havencompat.colorwheel.ColorwheelWarm.warm(pipeline, programSet.invoke(pack, dimension));
            LOG.once("colorwheel:" + round + ":" + dim[1], "Colorwheel's shader sources for {}:{} prepared behind the loading screen ({} sources, {} ms)",
                    dim[0], dim[1], sources, (System.nanoTime() - t) / 1_000_000);
        }
    }

    /**
     * Every Iris pipeline that exists for this dimension id now: the manager's own and Immersive Portals' portal views.
     * Empty before Iris has a manager or a pipeline for it.
     */
    public static List<Object> pipelinesOf(String namespace, String name, Class<?> idType) throws ReflectiveOperationException {
        return pipelinesOf(idType.getConstructor(String.class, String.class).newInstance(namespace, name));
    }

    public static List<Object> pipelinesOf(Object dimension) throws ReflectiveOperationException {
        List<Object> out = new java.util.ArrayList<>();
        Object manager = Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager").invoke(null);
        if (!(manager instanceof Pipelines own)) return out;
        Object main = own.haven_compat$pipelines().get(dimension);
        if (main != null && main.getClass().getSimpleName().equals("IrisRenderingPipeline")) out.add(main);
        for (Map<?, ?> view : portalViews()) {
            Object pipeline = view.get(dimension);
            if (pipeline != null && pipeline.getClass().getSimpleName().equals("IrisRenderingPipeline") && !out.contains(pipeline)) out.add(pipeline);
        }
        return out;
    }

    /** A prewarm round is to be expected at a join: this is on, not broken, and a shader pack is in use. */
    static boolean expected() {
        if (broken) return false;
        try {
            return enabled() && ((java.util.Optional<?>) Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPack").invoke(null)).isPresent();
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;   // no Iris
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
