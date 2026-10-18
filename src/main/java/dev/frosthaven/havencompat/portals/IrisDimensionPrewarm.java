package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Builds Iris's shader pipelines for the other dimensions while the player is still joining, instead of at the first
 * look through a portal into that dimension.
 * <p>
 * Iris keeps one pipeline per dimension and builds it on first use ({@code PipelineManager.preparePipeline}). With
 * Immersive Portals that first use is the first frame a portal to that dimension is in view: about one second of
 * shader linking on the render thread, then {@code LevelRenderer.allChanged()} (a full chunk rebuild, because the new
 * pipeline's block id maps differ), which Immersive Portals postpones while a portal renders. Seen 2026-10-18 on an
 * M1 Pro: pipeline at 16:50:35.85, Sodium relink 16:50:40.8, reload 16:50:41, several seconds of jitter. It happens
 * once per dimension per session, so this moves it into the join: as soon as Iris has built the pipeline of the
 * dimension the player arrives in (the first frame behind the loading screen), the other two are built and the
 * player's is made current again (a switch to a pipeline that exists is a map lookup, no rebuild). A shader pack
 * reload ({@code destroyPipeline}) clears Iris's pipelines and bumps its version counter; the next tick in a level
 * builds them again.
 * <p>
 * Reflection only, like {@link PortalShaderGuard}: Iris absent, no shader pack in use, or its internals moved means
 * one log line and nothing else. Client tick (the render thread), never inside a frame.
 */
public final class IrisDimensionPrewarm {
    private static final Log LOG = new Log("[iris prewarm] ");
    private static final String IRIS = "net.irisshaders.iris.Iris";
    private static final String NAMESPACED_ID = "net.irisshaders.iris.shaderpack.materialmap.NamespacedId";
    /** The dimensions Haven's portals lead to and from. */
    static final List<ResourceLocation> DIMENSIONS = List.of(
            ResourceLocation.withDefaultNamespace("overworld"),
            ResourceLocation.withDefaultNamespace("the_nether"),
            ResourceLocation.withDefaultNamespace("the_end"));
    private static Method getCurrentPack, getPipelineManager, preparePipeline, getPipelineNullable, versionCounter;
    private static Constructor<?> namespacedId;
    private static boolean done, broken;
    private static int doneAtVersion = -1, tick;

    private IrisDimensionPrewarm() {}

    public static void register(IEventBus bus) {
        if (!ModList.get().isLoaded("iris")) return;
        bus.addListener(ClientTickEvent.Post.class, IrisDimensionPrewarm::onTick);
    }

    private static void onTick(ClientTickEvent.Post event) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            if (getCurrentPack == null) lookUp();
            Object manager = getPipelineManager.invoke(null);
            int version = (Integer) versionCounter.invoke(manager);
            if (done) {
                if (++tick % 20 != 0 || version == doneAtVersion) return;   // once a second: a shader reload cleared them
                done = false;
                LOG.once("rearm:" + version, "shader pack reloaded; the other dimensions' pipelines are built again");
            }
            if (!(getCurrentPack.invoke(null) instanceof Optional<?> pack) || pack.isEmpty()) {   // no shaders: nothing to build
                done = true;
                doneAtVersion = version;
                return;
            }
            Object current = getPipelineNullable.invoke(manager);
            if (current == null || current.getClass().getSimpleName().startsWith("Vanilla")) return;   // the player's is not built yet: next tick
            ResourceLocation here = mc.level.dimension().location();
            for (ResourceLocation dim : DIMENSIONS) {
                if (dim.equals(here)) continue;
                long t = System.nanoTime();
                preparePipeline.invoke(manager, namespacedId.newInstance(dim.getNamespace(), dim.getPath()));
                LOG.once("warm:" + dim + ":" + version, "shader pipeline for {} built while joining ({} ms)", dim,
                        (System.nanoTime() - t) / 1_000_000);
            }
            preparePipeline.invoke(manager, namespacedId.newInstance(here.getNamespace(), here.getPath()));   // the player's back: a lookup
            done = true;
            doneAtVersion = (Integer) versionCounter.invoke(manager);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            LOG.error("prewarm", "could not pre-build Iris's pipelines; portals build them on first look as before", e);
        }
    }

    private static void lookUp() throws ReflectiveOperationException {
        Class<?> iris = Class.forName(IRIS), id = Class.forName(NAMESPACED_ID);
        Method manager = iris.getMethod("getPipelineManager");
        Class<?> managerType = manager.getReturnType();
        namespacedId = id.getConstructor(String.class, String.class);
        preparePipeline = managerType.getMethod("preparePipeline", id);
        getPipelineNullable = managerType.getMethod("getPipelineNullable");
        versionCounter = managerType.getMethod("getVersionCounterForSodiumShaderReload");
        getPipelineManager = manager;
        getCurrentPack = iris.getMethod("getCurrentPack");   // last: set means every lookup above succeeded
    }
}
