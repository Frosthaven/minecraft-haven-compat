package dev.frosthaven.havencompat.colorwheel;

import dev.djefrey.colorwheel.compile.ClrwlProgramSources;
import dev.djefrey.colorwheel.compile.ClrwlProgramSources.PatchedGbuffersSources;
import dev.djefrey.colorwheel.compile.ClrwlPrograms;
import dev.djefrey.colorwheel.compile.ClrwlShaderKey;
import dev.djefrey.colorwheel.engine.ClrwlEngine;
import dev.djefrey.colorwheel.shaderpack.ClrwlProgramId;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.material.CutoutShader;
import dev.engine_room.flywheel.api.material.FogShader;
import dev.engine_room.flywheel.api.material.LightShader;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.portals.PipelinePrewarm;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Colorwheel rewrites the shader pack's program sources (a parse and transform in Java, 30 to 50 ms per program) once
 * for every Iris pipeline: the same work again for each dimension and for each of Immersive Portals' extra portal-view
 * pipelines, on the render thread, the first time a pipeline has to draw a Create part. With portals that first time
 * is the moment a portal comes into view: 0.3 to 0.7 s of stall (2026-11-14), once per pipeline per session.
 * <p>
 * The rewrite depends only on the dimension's program set, the program, the transparency mode, the backend's buffer
 * offset and the pipeline's texture map, so {@link #cached} keeps one result per such key for all pipelines, and
 * {@link #warm} fills it for a dimension behind the loading screen (called by the Iris prewarm for all three vanilla
 * dimensions).
 * <p>
 * The programs themselves are the larger part in a real world. Colorwheel compiles and links one program per kind of
 * part (a {@code ClrwlShaderKey}) in each pipeline, at that pipeline's first draw: 1.4 to 1.9 s per pipeline in Shane's
 * world on a cold driver cache, three pipelines in a row at the first look at a portal (2026-11-15, 0.32.1). At a join
 * Colorwheel does not know yet which keys it will need, so this class keeps the list: {@link #compiled} writes every
 * key Colorwheel builds to {@value #FILE} in the game folder (by the names of its shader parts), and
 * {@link #warmEngines} builds the listed programs, and the six culling programs of the indirect backend, for every
 * pipeline of every Colorwheel engine behind the loading screen. A key whose parts no loaded class names yet (another
 * mod's instance type) is skipped; it is built at first use as before.
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

    /** The recorded program list, one key per line; the player's own, in the game folder. */
    public static final String FILE = "haven/colorwheel-programs.txt";
    private static final String[] HOLDERS = {
        "dev.engine_room.flywheel.lib.instance.InstanceTypes", "com.simibubi.create.foundation.render.AllInstanceTypes",
        "dev.engine_room.flywheel.lib.material.StandardMaterialShaders", "dev.engine_room.flywheel.lib.material.FogShaders",
        "dev.engine_room.flywheel.lib.material.CutoutShaders", "dev.engine_room.flywheel.lib.material.LightShaders"};
    private static final long WAIT_MS = 20_000;
    private static final Set<String> LINES = new LinkedHashSet<>();
    private static final Map<String, Object> PARTS = new ConcurrentHashMap<>();   // "kind:name" -> the shader part object
    private static boolean loaded, replaying, programsBroken;

    /** At the head of Colorwheel's {@code ClrwlCompilationHarness.compile}: it builds a program it did not have. */
    public static void compiled(Object key) {
        if (replaying || programsBroken || !(key instanceof ClrwlShaderKey k)) return;
        try {
            load();
            remember(k);
            String line = write(k);
            if (LINES.add(line)) {
                Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(FILE);
                Files.createDirectories(file.getParent());
                Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            programsBroken = true;
            LOG.error("record", "could not record Colorwheel's programs; they are built at first use as before", e);
        }
    }

    /**
     * Started by the Iris prewarm (a join, a shader reload): the recorded programs for every pipeline of every engine,
     * as soon as Colorwheel has an engine. A Colorwheel engine belongs to a client level and is made at that level's
     * first drawn frame: the player's a moment after the join, the other dimensions' at the first look through a
     * portal. So this waits (one check per frame, up to {@value #WAIT_MS} ms) for the first engine, then makes the
     * three vanilla dimensions' client levels and engines (Immersive Portals makes those levels itself when a portal
     * loads and keeps them for the session) and builds everything in that one frame, while the world has just appeared.
     */
    public static void warmEngines() {
        if (programsBroken) {
            dev.frosthaven.havencompat.portals.PrewarmScreen.done();
            return;
        }
        long deadline = System.currentTimeMillis() + WAIT_MS;
        Minecraft.getInstance().tell(() -> whenEngineExists(deadline));
    }

    private static void whenEngineExists(long deadline) {
        if (ClrwlEngine.ENGINES.isEmpty()) {
            if (System.currentTimeMillis() < deadline && Minecraft.getInstance().level != null) {
                Minecraft.getInstance().tell(() -> whenEngineExists(deadline));
            } else {
                dev.frosthaven.havencompat.portals.PrewarmScreen.done();
            }
            return;
        }
        try {
            if (net.neoforged.fml.ModList.get().isLoaded("immersive_portals_core")) {
                Class<?> worlds = Class.forName("qouteall.imm_ptl.core.ClientWorldLoader");
                if ((boolean) worlds.getMethod("getIsInitialized").invoke(null)) {
                    Set<?> served = (Set<?>) worlds.getMethod("getServerDimensions").invoke(null);
                    Method world = worlds.getMethod("getWorld", net.minecraft.resources.ResourceKey.class);
                    // Flywheel makes a manager (and so an engine) only for the level that is current: ask as that level.
                    Method asLevel = worlds.getMethod("withSwitchedWorld", net.minecraft.client.multiplayer.ClientLevel.class, Runnable.class);
                    for (var dimension : List.of(net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER,
                            net.minecraft.world.level.Level.END)) {
                        if (served.contains(dimension)) {
                            Object level = world.invoke(null, dimension);
                            // The manager makes its engine at its first tick or frame ("lateInit"): ask for it now.
                            Runnable make = () -> {
                                Object manager = dev.engine_room.flywheel.api.visualization.VisualizationManager.get(
                                        (net.minecraft.world.level.LevelAccessor) level);
                                if (manager == null) return;
                                try {
                                    open(manager.getClass().getDeclaredMethod("lateInit")).invoke(manager);
                                } catch (ReflectiveOperationException e) {
                                    throw new IllegalStateException(e);
                                }
                            };
                            asLevel.invoke(null, level, make);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOG.error("levels", "could not make the other dimensions' renderers at the join; their programs are built at the first look", e);
        }
        try {
            for (ClrwlEngine engine : List.copyOf(ClrwlEngine.ENGINES)) warmEngine(engine);
        } finally {
            dev.frosthaven.havencompat.portals.PrewarmScreen.done();   // the loading screen never waits on a failure
        }
    }

    private static void warmEngine(Object engine) {
        if (programsBroken || !ClrwlEngine.ENGINES.contains(engine)) return;
        try {
            load();
            List<ClrwlShaderKey> keys = new ArrayList<>();
            for (String line : LINES) {
                ClrwlShaderKey key = read(line);
                if (key != null) keys.add(key);
            }
            if (keys.isEmpty()) {
                if (!LINES.isEmpty()) LOG.info("none of the {} recorded Colorwheel programs names parts that are loaded; nothing built ahead", LINES.size());
                return;
            }
            Object dimension = open(engine.getClass().getDeclaredField("dimension")).get(engine);
            Object drawManager = open(engine.getClass().getDeclaredField("drawManager")).get(engine);
            Method pipelineData = null;
            for (Method m : drawManager.getClass().getDeclaredMethods()) {
                if (m.getName().equals("getPipelineData") && m.getParameterCount() == 1) pipelineData = open(m);
            }
            if (pipelineData == null) throw new NoSuchMethodException("getPipelineData");
            long t = System.nanoTime();
            int pipelines = 0, built = 0;
            replaying = true;
            try {
                for (Object irisPipeline : PipelinePrewarm.pipelinesOf(dimension)) {
                    Object programs = open(pipelineData.invoke(drawManager, irisPipeline).getClass().getRecordComponents()[0].getAccessor())
                            .invoke(pipelineData.invoke(drawManager, irisPipeline));
                    Method get = programs.getClass().getMethod("get", ClrwlShaderKey.class);
                    for (ClrwlShaderKey key : keys) {
                        if (get.invoke(programs, key) != null) built++;
                    }
                    for (Method m : programs.getClass().getMethods()) {
                        if (m.getName().equals("getCullingProgram")) {
                            for (Object culling : m.getParameterTypes()[0].getEnumConstants()) m.invoke(programs, culling);
                        }
                    }
                    pipelines++;
                }
            } finally {
                replaying = false;
            }
            if (pipelines == 0) {
                LOG.info("no shader pipeline for {} yet; Colorwheel's programs for it are built at first use", dimension);
            } else {
                LOG.info("Colorwheel's programs for {} prepared ahead of their first draw ({} pipelines, {} programs, {} ms)",
                        dimension, pipelines, built, (System.nanoTime() - t) / 1_000_000);
            }
        } catch (Exception | LinkageError e) {
            programsBroken = true;
            LOG.error("programs", "could not prepare Colorwheel's programs ahead; they are built at first use as before", e);
        }
    }

    private static void load() throws Exception {
        if (loaded) return;
        loaded = true;
        for (String holder : HOLDERS) {
            try {
                for (Field f : Class.forName(holder).getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) remember(open(f).get(null));
                }
            } catch (ClassNotFoundException | LinkageError absent) {
                // that mod is not installed
            }
        }
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(FILE);
        if (Files.exists(file)) LINES.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    private static void remember(ClrwlShaderKey k) {
        remember(k.instanceType());
        remember(k.material());
        remember(k.fog());
        remember(k.cutout());
        remember(k.light());
    }

    private static void remember(Object part) {
        String name = name(part);
        if (name != null) PARTS.putIfAbsent(name, part);
    }

    /** "kind:shader names" of a shader part; null for anything else. */
    private static String name(Object part) {
        if (part instanceof InstanceType<?> t) return "instance:" + t.vertexShader() + "+" + t.cullShader();
        if (part instanceof MaterialShaders m) return "material:" + m.vertexSource() + "+" + m.fragmentSource();
        if (part instanceof FogShader f) return "fog:" + f.source();
        if (part instanceof CutoutShader c) return "cutout:" + c.source();
        if (part instanceof LightShader l) return "light:" + l.source();
        return null;
    }

    private static String write(ClrwlShaderKey k) {
        return String.join(" ", name(k.instanceType()), name(k.material()), name(k.fog()), name(k.cutout()), name(k.light()),
                k.transparency().name(), k.context().name(), String.valueOf(k.isShadow()), String.valueOf(k.isDebugEnabled()), k.oit().name());
    }

    /** The key of a recorded line; null when a part of it is not known in this session (yet). */
    private static ClrwlShaderKey read(String line) {
        String[] f = line.split(" ");
        if (f.length != 10) return null;
        Object instance = PARTS.get(f[0]), material = PARTS.get(f[1]), fog = PARTS.get(f[2]), cutout = PARTS.get(f[3]), light = PARTS.get(f[4]);
        if (instance == null || material == null || fog == null || cutout == null || light == null) return null;
        try {
            return new ClrwlShaderKey((InstanceType<?>) instance, (MaterialShaders) material, (FogShader) fog, (CutoutShader) cutout,
                    (LightShader) light, Transparency.valueOf(f[5]), dev.engine_room.flywheel.backend.compile.ContextShader.valueOf(f[6]),
                    Boolean.parseBoolean(f[7]), Boolean.parseBoolean(f[8]), ClrwlPrograms.OitMode.valueOf(f[9]));
        } catch (IllegalArgumentException renamed) {
            return null;
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
