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
import dev.frosthaven.havencompat.portals.WarmQueue;
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
 * {@link #queueEngines} builds the listed programs, and the six culling programs of the indirect backend, for every
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
    public static void queueEngines(int expected, Runnable after) {
        if (programsBroken) {
            WarmQueue.add(after);
            return;
        }
        long deadline = System.currentTimeMillis() + WAIT_MS;
        // Wait (one check per frame) until Colorwheel has an engine; give up quietly after the deadline or without a level.
        WarmQueue.add(() -> !ClrwlEngine.ENGINES.isEmpty() || System.currentTimeMillis() >= deadline || Minecraft.getInstance().level == null);
        WarmQueue.add(() -> {
            if (!ClrwlEngine.ENGINES.isEmpty()) {
                makeEngines(List.of(net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER, net.minecraft.world.level.Level.END));
                for (ClrwlEngine engine : List.copyOf(ClrwlEngine.ENGINES)) queueEngine(engine, null);
            }
            WarmQueue.add(after);   // ends the loading screen's hold, after the programs
        });
        WarmQueue.reserve(expected);
    }

    /** The client levels, Flywheel managers and Colorwheel engines of these dimensions, made now (Immersive Portals only). */
    public static void makeEngines(List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> dimensions) {
        try {
            if (net.neoforged.fml.ModList.get().isLoaded("immersive_portals_core")) {
                Class<?> worlds = Class.forName("qouteall.imm_ptl.core.ClientWorldLoader");
                if ((boolean) worlds.getMethod("getIsInitialized").invoke(null)) {
                    Set<?> served = (Set<?>) worlds.getMethod("getServerDimensions").invoke(null);
                    Method world = worlds.getMethod("getWorld", net.minecraft.resources.ResourceKey.class);
                    // Flywheel makes a manager (and so an engine) only for the level that is current: ask as that level.
                    Method asLevel = worlds.getMethod("withSwitchedWorld", net.minecraft.client.multiplayer.ClientLevel.class, Runnable.class);
                    for (var dimension : dimensions) {
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
            LOG.error("levels", "could not make the other dimensions' renderers ahead; their programs are built at the first look", e);
        }
    }

    /** the uniforms Colorwheel sets by name at every draw; a program asks the driver for each one's place at its first draw */
    private static final String[] DRAW_UNIFORMS = {"_clrwl_blendFunc", "_clrwl_atlasSize", "_clrwl_renderPhase", "_flw_baseInstance",
        "_clrwl_blockEntityIdUniform", "_clrwl_entityIdUniform"};

    /**
     * A program's uniform places, asked for now. Flywheel's {@code GlProgram} keeps them in a map and asks the driver
     * the first time a name is used, which is the program's first draw: the question makes the driver finish the
     * program, 0.1 to 0.3 s over the first Colorwheel draws on a portal's far side (2026-11-22). Asked here, the map is
     * full before the first draw: the fixed names, and every uniform the program has.
     */
    private static void uniforms(dev.engine_room.flywheel.backend.gl.shader.GlProgram program) {
        for (String name : DRAW_UNIFORMS) program.getUniformLocation(name);
        int handle = program.handle();
        int count = org.lwjgl.opengl.GL20.glGetProgrami(handle, org.lwjgl.opengl.GL20.GL_ACTIVE_UNIFORMS);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var size = stack.mallocInt(1);
            var type = stack.mallocInt(1);
            for (int i = 0; i < count; i++) {
                String name = org.lwjgl.opengl.GL20.glGetActiveUniform(handle, i, size, type);
                if (!name.isEmpty() && !name.startsWith("gl_")) program.getUniformLocation(name);
            }
        }
    }

    private record Spare(Object backend, Object manager) {}
    /** draw managers built at the title screen, by the dimension's program set, until an engine takes them */
    private static final Map<Object, Spare> SPARES = new java.util.IdentityHashMap<>();

    /**
     * At the title screen, after the pipelines: a draw manager per vanilla dimension with every program built, kept as a
     * spare. Colorwheel makes a draw manager (the holder of its programs) when it makes a level's engine, from the shader
     * pack and the dimension's program set only; no level is needed for it. {@code mixin/ColorwheelSpareMixin} gives the
     * spare to the engine when the level appears, so a join links no program at all. A spare that no engine took is
     * deleted when Iris destroys its pipelines ({@link #dropSpares}).
     */
    public static void queueSpares(Class<?> idType) {
        if (programsBroken) return;
        for (String[] dim : PipelinePrewarm.DIMENSIONS) {
            WarmQueue.add(() -> {
                try {
                    var pack = net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
                    Object backend = dev.engine_room.flywheel.api.backend.BackendManager.currentBackend();
                    if (pack == null) return;
                    var id = new net.irisshaders.iris.shaderpack.materialmap.NamespacedId(dim[0], dim[1]);
                    var set = pack.getProgramSet(id);
                    if (SPARES.containsKey(set)) return;
                    boolean fallback = ((dev.djefrey.colorwheel.accessors.iris.ProgramSetAccessor) set).colorwheel$isFallbackMode();
                    var sources = dev.engine_room.flywheel.backend.compile.FlwPrograms.SOURCES;
                    Object manager;
                    if (backend == dev.djefrey.colorwheel.ClrwlBackend.IRIS_INDIRECT) {
                        manager = dev.djefrey.colorwheel.indirect.ClrwlIndirectDrawManager.build(sources, pack, set, fallback);
                    } else if (backend == dev.djefrey.colorwheel.ClrwlBackend.IRIS_INSTANCING) {
                        manager = dev.djefrey.colorwheel.instancing.ClrwlInstancedDrawManager.build(sources, pack, set, fallback);
                    } else {
                        return;   // Colorwheel is not the backend: nothing to prepare
                    }
                    if (manager == null) return;
                    SPARES.put(set, new Spare(backend, manager));
                    for (Object irisPipeline : pipelinesFor(id, null)) WarmQueue.add(() -> warmManager(manager, id, irisPipeline));
                } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                    programsBroken = true;
                    LOG.error("spares", "could not build Colorwheel's programs at the title screen; they are built at the join as before", e);
                }
            });
        }
    }

    /** From the mixin, in an engine's constructor: the spare for this program set, if the backend is still the same. */
    public static Object spare(Object programSet) {
        Spare spare = SPARES.remove(programSet);
        if (spare == null) return null;
        if (spare.backend != dev.engine_room.flywheel.api.backend.BackendManager.currentBackend()) {
            delete(spare.manager);
            return null;
        }
        LOG.info("an engine took the draw manager that was built at the title screen: no Colorwheel program is linked at this join");
        return spare.manager;
    }

    /**
     * From the mixin, when an engine is deleted: its draw manager becomes the spare for its program set again, programs
     * and all, unless one is waiting already. Flywheel makes a level's engine, deletes it a moment later and makes
     * another one at a join (the first took the spare and its programs were deleted with it: 4 s of linking again,
     * 2026-11-22); and a player who leaves a world and joins again gets a new engine for the same dimension. A draw
     * manager's engine state is emptied first ({@link #reset}). False: delete it as before.
     */
    public static boolean recycle(Object pack, Object dimension, Object manager) {
        if (programsBroken || dropping || manager == null) return false;
        try {
            var current = net.irisshaders.iris.Iris.getCurrentPack().orElse(null);
            if (current == null || current != pack) return false;   // another shader pack by now
            Object set = current.getProgramSet((net.irisshaders.iris.shaderpack.materialmap.NamespacedId) dimension);
            if (SPARES.containsKey(set)) return false;
            reset(manager);
            SPARES.put(set, new Spare(dev.engine_room.flywheel.api.backend.BackendManager.currentBackend(), manager));
            LOG.once("recycle", "a deleted engine's draw manager is kept, with its programs, for the next engine of that dimension");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warnOnce("recycle-reset", "could not empty a deleted engine's draw manager; it is deleted as before: {}", e.toString());
            return false;
        }
    }

    /**
     * What a draw manager holds for its engine goes, as in its own delete(): the instancers (keyed by the old level's
     * visuals and contraption environments), the indirect culling groups with their buffers, the draw lists. Left are
     * the programs, the per-pipeline data, the buffers that are written anew each frame, and the mesh pool (model
     * meshes, which are not the level's; one whose draws went stays uploaded, bounded by the game's models).
     * Throws when a field is not as expected: the caller then deletes the manager whole.
     */
    private static void reset(Object manager) throws ReflectiveOperationException {
        Class<?> base = dev.djefrey.colorwheel.engine.ClrwlDrawManager.class;
        Map<?, ?> instancers = (Map<?, ?>) field(base, "instancers").get(manager);
        java.util.Collection<?> queue = (java.util.Collection<?>) field(base, "initializationQueue").get(manager);
        Class<?> type = manager.getClass();
        if (manager instanceof dev.djefrey.colorwheel.indirect.ClrwlIndirectDrawManager) {
            Map<?, ?> groups = (Map<?, ?>) field(type, "cullingGroups").get(manager);
            List<?> shadowSolid = (List<?>) field(type, "shadowSolidDraws").get(manager);
            List<?> shadowTranslucent = (List<?>) field(type, "shadowTranslucentDraws").get(manager);
            for (Object group : groups.values()) ((dev.djefrey.colorwheel.indirect.ClrwlIndirectCullingGroup<?>) group).delete();
            groups.clear();
            shadowSolid.clear();
            shadowTranslucent.clear();
        } else if (manager instanceof dev.djefrey.colorwheel.instancing.ClrwlInstancedDrawManager) {
            List<?> all = (List<?>) field(type, "allDraws").get(manager);
            List<?> solid = (List<?>) field(type, "solidDraws").get(manager), translucent = (List<?>) field(type, "translucentDraws").get(manager),
                    oit = (List<?>) field(type, "oitDraws").get(manager);
            for (Object instancer : instancers.values()) instancer.getClass().getMethod("delete").invoke(instancer);
            for (Object draw : all) draw.getClass().getMethod("delete").invoke(draw);
            solid.clear(); translucent.clear(); oit.clear(); all.clear();
        } else {
            throw new NoSuchFieldException("unknown draw manager " + type.getName());
        }
        instancers.clear();
        queue.clear();
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        java.lang.reflect.Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static boolean dropping;

    /**
     * A world exit: every level's Flywheel visualization is reset, Immersive Portals' other client levels included.
     * Flywheel deleted only the player's level's; the Nether's and End's engines (and with them those old levels) stayed
     * registered, so their draw managers never became spares and the next join built new ones: about 1.3 s of Colorwheel
     * programs (Complementary, 2026-11-22). Deleted now, each manager is kept by {@link #recycle} for the next join.
     */
    public static void worldLeft() {
        if (programsBroken) return;
        try {
            Class.forName("dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl").getMethod("resetAll").invoke(null);
            LOG.once("left", "every level's Colorwheel engine was released at the world exit; their programs are kept for the next join");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOG.warnOnce("left", "could not release the other levels' Colorwheel engines at the world exit: {}", e.toString());
        }
    }

    /** Iris destroyed its pipelines: the spares' programs belong to them. */
    public static void dropSpares() {
        dropping = true;
        try {
            for (Spare spare : SPARES.values()) delete(spare.manager);
            SPARES.clear();
        } finally {
            dropping = false;
        }
    }

    private static void delete(Object manager) {
        try {
            manager.getClass().getMethod("delete").invoke(manager);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warnOnce("spare-delete", "could not delete an unused Colorwheel draw manager: {}", e.toString());
        }
    }

    /** A portal links these two dimensions: their levels and engines, then every engine's programs for both. */
    public static void queueLink(String from, String to) {
        if (programsBroken) return;
        WarmQueue.add(() -> {
            if (ClrwlEngine.ENGINES.isEmpty()) return;
            List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> keys = new ArrayList<>();
            for (String side : List.of(from, to)) {
                keys.add(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(side)));
            }
            makeEngines(keys);
            for (ClrwlEngine engine : List.copyOf(ClrwlEngine.ENGINES)) {
                try {
                    Object dimension = open(engine.getClass().getDeclaredField("dimension")).get(engine);
                    String name = dimension.getClass().getMethod("getNamespace").invoke(dimension) + ":" + dimension.getClass().getMethod("getName").invoke(dimension);
                    if (name.equals(from) || name.equals(to)) queueEngine(engine, null);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    /**
     * One step per pipeline this engine draws with (see {@link #pipelinesFor}); {@code only}: just the pipelines of that
     * dimension name ("namespace:path"), for a dimension that came later.
     */
    public static void queueEngine(Object engine, String only) {
        if (programsBroken) return;
        try {
            Object dimension = open(engine.getClass().getDeclaredField("dimension")).get(engine);
            for (Object irisPipeline : pipelinesFor(dimension, only)) {
                WarmQueue.add(() -> warmPair(engine, dimension, irisPipeline));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            programsBroken = true;
            LOG.error("programs", "could not prepare Colorwheel's programs ahead; they are built at first use as before", e);
        }
    }

    private static void warmPair(Object engine, Object dimension, Object irisPipeline) {
        if (programsBroken || !ClrwlEngine.ENGINES.contains(engine)) return;
        try {
            warmManager(open(engine.getClass().getDeclaredField("drawManager")).get(engine), dimension, irisPipeline);
        } catch (ReflectiveOperationException | RuntimeException e) {
            programsBroken = true;
            LOG.error("programs", "could not prepare Colorwheel's programs ahead; they are built at first use as before", e);
        }
    }

    /** Every recorded program, and the culling programs, of this draw manager for this pipeline. Built ones cost nothing. */
    private static void warmManager(Object drawManager, Object dimension, Object irisPipeline) {
        if (programsBroken) return;
        try {
            load();
            List<ClrwlShaderKey> keys = new ArrayList<>();
            for (String line : LINES) {
                ClrwlShaderKey key = read(line);
                if (key != null) keys.add(key);
            }
            if (keys.isEmpty()) {
                if (!LINES.isEmpty()) LOG.once("no keys", "none of the {} recorded Colorwheel programs names parts that are loaded; nothing built ahead", LINES.size());
                return;
            }
            Method pipelineData = null;
            for (Method m : drawManager.getClass().getDeclaredMethods()) {
                if (m.getName().equals("getPipelineData") && m.getParameterCount() == 1) pipelineData = open(m);
            }
            if (pipelineData == null) throw new NoSuchMethodException("getPipelineData");
            long t = System.nanoTime();
            int built = 0;
            replaying = true;
            try {
                Object data = pipelineData.invoke(drawManager, irisPipeline);
                Object programs = open(data.getClass().getRecordComponents()[0].getAccessor()).invoke(data);
                Method get = programs.getClass().getMethod("get", ClrwlShaderKey.class);
                for (ClrwlShaderKey key : keys) {
                    Object program = get.invoke(programs, key);
                    if (program != null) built++;
                    if (program instanceof dev.engine_room.flywheel.backend.gl.shader.GlProgram gl) uniforms(gl);
                }
                for (Method m : programs.getClass().getMethods()) {
                    if (m.getName().equals("getCullingProgram")) {
                        for (Object culling : m.getParameterTypes()[0].getEnumConstants()) m.invoke(programs, culling);
                    }
                }
            } finally {
                replaying = false;
            }
            long ms = (System.nanoTime() - t) / 1_000_000;
            if (ms >= 5) LOG.info("Colorwheel's programs for {} with shader pipeline {} prepared ahead of their first draw ({} programs, {} ms)",
                    dimension, Integer.toHexString(System.identityHashCode(irisPipeline)), built, ms);
        } catch (Exception | LinkageError e) {
            programsBroken = true;
            LOG.error("programs", "could not prepare Colorwheel's programs ahead; they are built at first use as before", e);
        }
    }

    /**
     * The pipelines an engine draws with. Its own dimension's, and the other portal-linked dimension's too: the far half
     * of a train carriage or ship in a portal is drawn by its own level's engine inside the other dimension's view, with
     * that dimension's pipeline. Those pairs were built in play, 0.75 to 1.8 s at a train's first crossings (2026-11-22).
     */
    private static List<Object> pipelinesFor(Object dimension, String only) throws ReflectiveOperationException {
        String self = dimension.getClass().getMethod("getNamespace").invoke(dimension) + ":" + dimension.getClass().getMethod("getName").invoke(dimension);
        List<Object> out = new ArrayList<>();
        if (only == null || only.equals(self)) out.addAll(PipelinePrewarm.pipelinesOf(dimension));
        for (String link : PipelinePrewarm.LINKS) {
            String[] ends = link.split("\\|");
            String other = ends[0].equals(self) ? ends[1] : ends[1].equals(self) ? ends[0] : null;
            if (other == null || only != null && !only.equals(other)) continue;
            String[] id = other.split(":", 2);
            for (Object pipeline : PipelinePrewarm.pipelinesOf(id[0], id[1], dimension.getClass())) if (!out.contains(pipeline)) out.add(pipeline);
        }
        return out;
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
