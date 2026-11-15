package dev.frosthaven.havencompat.colorwheel;

import dev.djefrey.colorwheel.compile.ClrwlProgramSources;
import dev.djefrey.colorwheel.compile.ClrwlProgramSources.PatchedGbuffersSources;
import dev.djefrey.colorwheel.compile.ClrwlPrograms;
import dev.djefrey.colorwheel.shaderpack.ClrwlProgramId;
import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Colorwheel rewrites the shader pack's program sources (a parse and transform in Java, 30 to 50 ms per program) once
 * for every Iris pipeline: the same work again for each dimension and for each of Immersive Portals' extra portal-view
 * pipelines, on the render thread, the first time a pipeline has to draw a Create part. With portals that first time
 * is the moment a portal comes into view: 0.3 to 0.7 s of stall (2026-11-14), once per pipeline per session.
 * <p>
 * The rewrite depends only on the dimension's program set, the program, the transparency mode, the backend's buffer
 * offset and the pipeline's texture map, so {@link #cached} keeps one result per such key for all pipelines, and
 * {@link #warm} fills it for a dimension behind the loading screen (called by the Iris prewarm for all three vanilla
 * dimensions). What stays at a portal's first view is the driver's compile and link, a few milliseconds per program.
 */
public final class ColorwheelWarm {
    private record Key(ClrwlProgramId id, ClrwlPrograms.OitMode oit, int ssboOffset, Object textureMap) {}

    private static final Log LOG = new Log("[colorwheel warm] ");
    /** Colorwheel's indirect backend moves the pack's shader storage bindings up by this; its instancing backend by 0. */
    private static final int INDIRECT_OFFSET = 10;
    private static final Map<Object, Map<Key, PatchedGbuffersSources>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static Field programSet, pipeline;
    private static Method textureMap;
    private static boolean broken;

    private ColorwheelWarm() {}

    /** Around {@code ClrwlProgramSources.getGbuffersSources}: one rewrite per key for every pipeline of a program set. */
    public static PatchedGbuffersSources cached(Object sources, ClrwlProgramId id, ClrwlPrograms.OitMode oit, int ssboOffset,
            Supplier<PatchedGbuffersSources> original) {
        if (broken) return original.get();
        Object set;
        Key key;
        try {
            if (programSet == null) {
                programSet = open(ClrwlProgramSources.class.getDeclaredField("programSet"));
                pipeline = open(ClrwlProgramSources.class.getDeclaredField("irisPipeline"));
            }
            set = programSet.get(sources);
            Object irisPipeline = pipeline.get(sources);
            if (textureMap == null) textureMap = irisPipeline.getClass().getMethod("getTextureMap");
            key = new Key(id, oit, ssboOffset, textureMap.invoke(irisPipeline));
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOG.error("cache", "could not share Colorwheel's rewritten shader sources between pipelines; it rewrites them per pipeline as before", e);
            return original.get();
        }
        Map<Key, PatchedGbuffersSources> ofSet = CACHE.computeIfAbsent(set, s -> new ConcurrentHashMap<>());
        PatchedGbuffersSources known = ofSet.get(key);
        if (known != null) return known;
        PatchedGbuffersSources made = original.get();   // may throw for a program the pack does not have: not kept
        ofSet.put(key, made);
        return made;
    }

    /** Rewrites every program of this dimension's program set now. Returns how many sources it has for the set. */
    public static int warm(Object irisPipeline, Object set) {
        if (broken || irisPipeline == null || set == null) return 0;
        try {
            Constructor<?> make = ClrwlProgramSources.class.getConstructors()[0];
            ClrwlProgramSources sources = (ClrwlProgramSources) make.newInstance(irisPipeline, set);
            int offset = indirect() ? INDIRECT_OFFSET : 0;
            for (ClrwlProgramId id : ClrwlProgramId.values()) {
                for (ClrwlPrograms.OitMode oit : ClrwlPrograms.OitMode.values()) {
                    try {
                        sources.getGbuffersSources(id, oit, offset);
                    } catch (RuntimeException noSuchProgram) {
                        // the pack has no such program (and no base for it): Colorwheel never asks for it either
                    }
                }
            }
            Map<Key, PatchedGbuffersSources> ofSet = CACHE.get(set);
            return ofSet == null ? 0 : ofSet.size();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOG.error("warm", "could not prepare Colorwheel's shader sources behind the loading screen; it prepares them at first use as before", e);
            return 0;
        }
    }

    /** Flywheel's own test for the indirect backends (OpenGL 4.6 class drivers); Colorwheel's indirect backend needs the same. */
    private static boolean indirect() {
        try {
            return Class.forName("dev.engine_room.flywheel.backend.gl.GlCompat").getField("SUPPORTS_INDIRECT").getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return true;
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T open(T member) {
        member.setAccessible(true);
        return member;
    }
}
