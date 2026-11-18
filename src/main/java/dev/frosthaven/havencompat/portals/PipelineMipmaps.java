package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Iris's composite passes make mipmaps of some of a pipeline's render targets in every frame
 * ({@code CompositeRenderer.setupMipmapping}). The first time for a texture the driver allocates the whole mip chain:
 * in Shane's game the first three frames with a portal in view took 109 to 136 ms with the render thread in
 * {@code glGenerateTextureMipmap} for the portal view's pipeline (2026-11-18). This class makes those mipmaps once when
 * the pipeline is created (a join, behind the loading screen, with the pipeline prewarm), by Iris's own method, for
 * exactly the targets its passes will ask for.
 */
public final class PipelineMipmaps {
    private static final Log LOG = new Log("[iris mipmaps] ");
    private static final String COMPOSITE = "net.irisshaders.iris.pipeline.CompositeRenderer";
    private static final Set<Object> DONE = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean broken;

    private PipelineMipmaps() {}

    /** From the mixin, after every {@code preparePipeline}: once per pipeline object. Render thread only. */
    public static void warm(Object pipeline) {
        if (broken || pipeline == null || !DONE.add(pipeline)) return;
        if (pipeline.getClass().getSimpleName().startsWith("Vanilla")) return;   // shaders off
        try {
            Class<?> composite = Class.forName(COMPOSITE);
            Method setup = null;
            for (Method m : composite.getDeclaredMethods()) {
                if (m.getName().equals("setupMipmapping") && m.getParameterCount() == 2) setup = m;
            }
            if (setup == null) throw new NoSuchMethodException("CompositeRenderer.setupMipmapping");
            setup.setAccessible(true);
            Field passesField = field(composite, "passes"), targetsField = field(composite, "renderTargets");
            int made = 0;
            long t = System.nanoTime();
            for (Field f : pipeline.getClass().getDeclaredFields()) {
                if (f.getType() != composite) continue;
                f.setAccessible(true);
                Object renderer = f.get(pipeline);
                if (renderer == null) continue;
                Object targets = targetsField.get(renderer);
                Method get = targets.getClass().getMethod("get", int.class);
                for (Object pass : (List<?>) passesField.get(renderer)) {
                    Collection<?> mipmapped = (Collection<?>) field(pass.getClass(), "mipmappedBuffers").get(pass);
                    if (mipmapped == null || mipmapped.isEmpty()) continue;
                    Collection<?> alt = (Collection<?>) field(pass.getClass(), "stageReadsFromAlt").get(pass);
                    for (Object index : mipmapped) {
                        setup.invoke(null, get.invoke(targets, (int) (Integer) index), alt != null && alt.contains(index));
                        made++;
                    }
                }
            }
            if (made > 0) LOG.once("made", "mipmaps of a new shader pipeline's render targets made ahead of its first frame ({} targets, {} ms); logged for the first pipeline only", made, (System.nanoTime() - t) / 1_000_000);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            LOG.error("warm", "could not make a shader pipeline's mipmaps ahead of time; Iris makes them in its first frame as before", e);
        }
    }

    /** The field {@code name} of {@code type} or a superclass (a pass class extends another). */
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
