package dev.frosthaven.havencompat.colorwheel;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Immersive Portals' cuts for Create's instanced parts, for Colorwheel (the Flywheel backend that runs with Iris
 * shaders). Immersive Portals cuts those parts in two ways: by its portal plane in a portal view, and by a ship's
 * portal plane while a Sable ship is half through a portal (the near half draws here, the far half through the
 * portal). It does both by patching Flywheel's own engine: its vertex shaders and its embedded environments.
 * Colorwheel has its own shaders and environments, so with Colorwheel a straddling ship's shafts and cogs drew whole,
 * on both sides of the portal.
 * <p>
 * This is the same cut on Colorwheel's classes. {@link #patch} adds two clip distances to every Colorwheel vertex
 * shader, with the uniform names Immersive Portals already feeds on each program bind (Colorwheel's programs are
 * Flywheel {@code GlProgram}s, so that hook still runs): the portal plane, and a ship plane. The ship plane comes per
 * embedded environment: a uniform at {@code setupDraw} on the instancing backend, a buffer indexed like Colorwheel's
 * matrix buffer on the indirect backend. {@link #beginPass} asks Immersive Portals for each environment's plane
 * ({@code IPStraddleRender.flywheelPlane}, and in its far-half pass {@code flywheelFarHalfPlane} for the ship it is
 * drawing; everything else is rejected there, as Immersive Portals does).
 * <p>
 * Train carriages in a portal use the same ship-plane slot. Immersive Portals keeps a map from a carriage's embedding to
 * the carriage and only fills it for Flywheel's environment type; {@link #registerCarriage} puts Colorwheel's
 * environment into that same map, so Immersive Portals' own bookkeeping (model rebuilds, known copies) sees the
 * carriage, and {@link #beginPass} reads the carriage's plane from Immersive Portals' own functions.
 * Reflection only; a failure is logged once and the parts draw uncut, as before.
 */
public final class ColorwheelCut {
    /** A Colorwheel embedded environment, by the mixin. */
    public interface Env {
        Vec3i haven_compat$origin();

        int haven_compat$index();

        boolean haven_compat$deleted();
    }

    public static final String ENGINE = "dev.djefrey.colorwheel.engine.ClrwlEngine";
    public static final String ENVIRONMENT = "dev.djefrey.colorwheel.engine.embed.EmbeddedEnvironment";
    public static final String COMPILATION = "dev.djefrey.colorwheel.compile.core.ClrwlCompilation";
    public static final String CARRIAGES = "qouteall.imm_ptl.core.compat.create_integration.IPCreateCarriageCrossingClient";
    /** Colorwheel's indirect backend uses shader storage bindings 0 to 9 and moves the shader pack's own up to 10 and above. */
    static final int BINDING = 28;
    private static final String SHIP_UNIFORM = "ipa_ShipClipEquation";
    private static final String MAIN = "void _clrwl_main(";
    private static final String SHADER_MAIN = "_clrwl_shader_main();";
    private static final String VERTEX_ONLY = "gl_VertexID";
    private static final String MATRICES = "_flw_matrices[draw.matrixIndex]";
    private static final float[] NEUTRAL = {0, 0, 0, 1};
    private static final float[] REJECT = {0, 0, 0, -1};
    private static final int CLIP_DISTANCE1 = 0x3001;
    private static final Log LOG = new Log("[colorwheel cut] ");
    private static final Map<Object, float[]> PLANES = new IdentityHashMap<>();
    private static final Map<Integer, Integer> LOCATIONS = new java.util.HashMap<>();
    private static boolean enabled, far, broken;
    private static int buffer, bufferSize;
    private static Boolean storage;
    private static Handles handles;
    private static Map<?, ?> carriageMap;
    private static boolean carriageMapBroken;

    private ColorwheelCut() {}

    /** The full source of a Colorwheel shader, right before it is compiled. Only vertex shaders change. */
    public static CharSequence patch(CharSequence source) {
        if (source == null) return null;
        String s = source.toString();
        int main = s.indexOf(MAIN);
        int call = s.lastIndexOf(SHADER_MAIN);
        if (main < 0 || !s.contains(VERTEX_ONLY)) return source;   // not a vertex shader (fragment shaders have a _clrwl_main too)
        if (call < main) {
            LOG.warnOnce("shader", "a Colorwheel vertex shader has no \"{}\": Colorwheel changed, its parts are not cut at portals", SHADER_MAIN);
            return source;
        }
        boolean indirect = s.contains(MATRICES);
        String declarations = "uniform vec4 iportal_ClippingEquation;\nuniform vec4 " + SHIP_UNIFORM + ";\n"
                + "vec4 hc_shipClip = vec4(0.0, 0.0, 0.0, 1.0);\nbool hc_shipClipSet = false;\n"
                + (indirect ? "#ifdef FLW_EMBEDDED\nlayout(std430, binding = " + BINDING + ") restrict readonly buffer HcShipClipBuffer {\n"
                        + "    vec4 hc_shipClips[];\n};\n#endif\n" : "");
        String clip = "vec3 hc_clipPos = flw_vertexPos.xyz;\n    " + SHADER_MAIN + "\n"
                + "    vec4 hc_clip = hc_shipClipSet ? hc_shipClip : " + SHIP_UNIFORM + ";\n"
                + "    gl_ClipDistance[0] = dot(hc_clipPos - flw_cameraPos, iportal_ClippingEquation.xyz) + iportal_ClippingEquation.w;\n"
                + "    gl_ClipDistance[1] = dot(hc_clipPos, hc_clip.xyz) + hc_clip.w;";
        s = s.substring(0, main) + declarations + s.substring(main, call) + clip + s.substring(call + SHADER_MAIN.length());
        if (indirect) {
            s = s.replaceAll("(_flw_unpackMatrices\\(_flw_matrices\\[draw\\.matrixIndex\\][^;]*;)",
                    "$1 hc_shipClip = hc_shipClips[draw.matrixIndex]; hc_shipClipSet = true;");
        }
        LOG.once("patched", "Colorwheel's vertex shaders get Immersive Portals' portal plane and ship plane");
        return s;
    }

    /** In {@code ClrwlEngine.render}, after its frame preparation (the environments have their matrix indices). */
    public static void beginPass(Object engine, Vec3i renderOrigin) {
        PLANES.clear();
        enabled = false;
        if (broken) return;
        try {
            if (handles == null) handles = new Handles(engine);
            ClientLevel level = Minecraft.getInstance().level;   // Immersive Portals sets it to the level it draws
            if (level == null) return;
            Object selection = handles.farHalf.get(null);
            far = selection != null;
            Object ship = null, session = null;
            if (far && handles.shipFarHalf.isInstance(selection)) {
                ship = handles.farShip.invoke(selection);
                session = handles.farSession.invoke(selection);
            }
            Map<?, ?> farCarriages = far && ship == null ? handles.captured(selection) : null;
            Collection<?> environments = (Collection<?>) handles.environments.get(handles.storage.invoke(engine));
            boolean ships = handles.sessionsEmpty != null && !(boolean) handles.sessionsEmpty.invoke(null);
            boolean carriages = handles.carriages != null && !handles.carriages.isEmpty();
            boolean straddling = far || ships || carriages;
            int maxIndex = 0;
            for (Object o : environments) {
                Env env = (Env) o;
                if (env.haven_compat$deleted()) continue;
                maxIndex = Math.max(maxIndex, env.haven_compat$index());
                if (!straddling) continue;
                Object root = root(o);
                Vec3i origin = ((Env) root).haven_compat$origin();
                Object carriage = carriages ? carriageOf(o, root) : null;
                float[] plane = null;
                if (far) {
                    if (ship != null) {
                        if (ofShip(level, origin, ship)) plane = (float[]) handles.farPlane.invoke(null, session, renderOrigin);
                    } else if (carriage != null && farCarriages != null) {
                        Object crossing = farCarriages.get(carriage);
                        if (crossing != null && !(boolean) handles.carriageOutdated.invoke(null, carriage, true)) {
                            plane = (float[]) handles.carriageFarPlane.invoke(null, carriage, crossing, renderOrigin);
                        }
                    }
                } else {
                    if (carriage != null) plane = (float[]) handles.carriagePlane.invoke(null, carriage, renderOrigin);
                    if (plane == null && ships) plane = (float[]) handles.nearPlane.invoke(null, level, origin, renderOrigin);
                }
                if (plane != null) PLANES.put(o, plane);
            }
            boolean cut = far || !PLANES.isEmpty();
            if (storageBuffers()) uploadPlanes(cut, maxIndex);
            if (!cut) return;
            GL11.glEnable(CLIP_DISTANCE1);
            enabled = true;
            LOG.once("cut", "cutting Colorwheel's parts of ships that are half through a portal");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            PLANES.clear();
            LOG.error("begin", "could not cut Colorwheel's parts of straddling ships; they draw whole", e);
        }
    }

    /**
     * The indirect shaders read this buffer on every embedded draw, so it always holds an entry per environment. Only on
     * a context with shader storage buffers: without them (macOS, OpenGL 4.1) Colorwheel runs its instancing backend,
     * which takes the plane from the uniform at {@link #setupDraw}, and each bind here was a GL_INVALID_ENUM.
     */
    private static void uploadPlanes(boolean cut, int maxIndex) {
        int size = (maxIndex + 1) * 4;
        if (cut || buffer == 0 || size > bufferSize) {
            float[] fill = far ? REJECT : NEUTRAL;
            float[] data = new float[size];
            for (int i = 0; i < size; i += 4) System.arraycopy(fill, 0, data, i, 4);
            for (Map.Entry<Object, float[]> e : PLANES.entrySet()) {
                System.arraycopy(e.getValue(), 0, data, ((Env) e.getKey()).haven_compat$index() * 4, 4);
            }
            if (buffer == 0) buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            bufferSize = cut ? 0 : size;   // after a cut the next pass writes the neutral values back
        }
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, buffer);
    }

    /** Shader storage buffers exist on this context (OpenGL 4.3 or the extension); asked once, on the render thread. */
    private static boolean storageBuffers() {
        if (storage == null) {
            GLCapabilities caps = GL.getCapabilities();
            storage = caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object;
            if (!storage) LOG.once("no storage", "no shader storage buffers on this OpenGL context: the ship plane goes to Colorwheel's instancing shaders as a uniform only");
        }
        return storage;
    }

    /**
     * Immersive Portals' {@code IPCreateCarriageCrossingClient.registerEmbedding}: it keeps only Flywheel's environment
     * type, so a Colorwheel environment goes into its map here (the map's keys are not read by type).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerCarriage(Object embedding, Object entity) {
        if (!(embedding instanceof Env) || entity == null || carriageMapBroken) return;
        try {
            if (carriageMap == null) {
                Class<?> client = Class.forName(CARRIAGES);
                if (!Class.forName("com.simibubi.create.content.trains.entity.CarriageContraptionEntity").isInstance(entity)) return;
                Field f = client.getDeclaredField("EMBEDDINGS");
                f.setAccessible(true);
                carriageMap = (Map) f.get(null);
            } else if (!entity.getClass().getName().endsWith("CarriageContraptionEntity")) {
                return;
            }
            ((Map) carriageMap).put(embedding, new java.lang.ref.WeakReference<>(entity));
            LOG.once("carriage", "train carriages drawn by Colorwheel are known to Immersive Portals");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            carriageMapBroken = true;
            LOG.error("carriage", "could not register a Colorwheel carriage with Immersive Portals; carriages in a portal draw whole", e);
        }
    }

    /** The carriage this environment (or its root) draws, from Immersive Portals' map. */
    private static Object carriageOf(Object environment, Object root) {
        Object reference = handles.carriages.get(environment);
        if (reference == null) reference = handles.carriages.get(root);
        return reference instanceof java.lang.ref.Reference<?> r ? r.get() : null;
    }

    /** At the end of an environment's {@code setupDraw}: its program is bound (the instancing backend). */
    public static void setupDraw(Object environment) {
        if (!enabled) return;
        float[] plane = PLANES.getOrDefault(environment, far ? REJECT : NEUTRAL);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int location = LOCATIONS.computeIfAbsent(program, p -> GL20.glGetUniformLocation(p, SHIP_UNIFORM));
        if (location >= 0) GL20.glUniform4f(location, plane[0], plane[1], plane[2], plane[3]);
    }

    /** At the end of {@code ClrwlEngine.render}. */
    public static void endPass() {
        if (enabled) {
            enabled = false;
            GL11.glDisable(CLIP_DISTANCE1);
        }
        PLANES.clear();
    }

    private static Object root(Object environment) throws ReflectiveOperationException {
        Object root = environment;
        for (int depth = 0; depth < 16; depth++) {
            Object parent = handles.parent.get(root);
            if (parent == null) break;
            root = parent;
        }
        return root;
    }

    /** The environment at this origin belongs to this ship: its origin is in the ship's plot (Sable). */
    private static boolean ofShip(ClientLevel level, Vec3i origin, Object ship) throws ReflectiveOperationException {
        Object container = handles.container.invoke(null, level);
        if (container == null) return false;
        Object plot = handles.plot.invoke(container, new ChunkPos(new BlockPos(origin)));
        return plot != null && handles.plotShip.invoke(plot) == ship;
    }

    /** Reflection, resolved once on the first pass. The Sable and the Create train parts are null without those mods. */
    private static final class Handles {
        final Method storage, sessionsEmpty, nearPlane, farPlane, farShip, farSession, container, plot, plotShip;
        final Method carriagePlane, carriageFarPlane, carriageOutdated;
        final Field environments, parent, farHalf;
        final Class<?> shipFarHalf;
        final Map<?, ?> carriages;
        private final Map<Class<?>, Field> capturedMaps = new java.util.HashMap<>();

        Handles(Object engine) throws ReflectiveOperationException {
            storage = engine.getClass().getMethod("environmentStorage");
            environments = open(Class.forName("dev.djefrey.colorwheel.engine.embed.EnvironmentStorage").getDeclaredField("environments"));
            parent = open(Class.forName(ENVIRONMENT).getDeclaredField("parent"));
            Class<?> straddle = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPFlywheelStraddleClip");
            farHalf = open(straddle.getDeclaredField("farHalf"));
            shipFarHalf = Class.forName(straddle.getName() + "$ShipFarHalf");
            if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
                farShip = open(shipFarHalf.getDeclaredMethod("ship"));
                farSession = open(shipFarHalf.getDeclaredMethod("session"));
                Class<?> render = Class.forName("qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleRender");
                Class<?> sessions = Class.forName("qouteall.imm_ptl.core.compat.sable_straddle.client.IPStraddleClientSessions");
                sessionsEmpty = sessions.getMethod("isEmpty");
                nearPlane = render.getMethod("flywheelPlane", ClientLevel.class, Vec3i.class, Vec3i.class);
                farPlane = render.getMethod("flywheelFarHalfPlane", Class.forName(sessions.getName() + "$Session"), Vec3i.class);
                Class<?> containers = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                container = containers.getMethod("getContainer", net.minecraft.world.level.Level.class);
                plot = containers.getMethod("getPlot", ChunkPos.class);
                plotShip = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot").getMethod("getSubLevel");
            } else {
                farShip = farSession = sessionsEmpty = nearPlane = farPlane = container = plot = plotShip = null;
            }
            if (net.neoforged.fml.ModList.get().isLoaded("create")) {
                Class<?> client = Class.forName(CARRIAGES);
                carriages = (Map<?, ?>) open(client.getDeclaredField("EMBEDDINGS")).get(null);
                Method plane = null, farPlaneOf = null, outdated = null;
                for (Method m : client.getDeclaredMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (m.getName().equals("flywheelPlane") && p.length == 2 && p[1] == Vec3i.class) plane = open(m);
                    if (m.getName().equals("farHalfPlane") && p.length == 3) farPlaneOf = open(m);
                    if (m.getName().equals("isOutdated") && p.length == 2) outdated = open(m);
                }
                if (plane == null || farPlaneOf == null || outdated == null) throw new NoSuchMethodException(CARRIAGES + " plane functions");
                carriagePlane = plane;
                carriageFarPlane = farPlaneOf;
                carriageOutdated = outdated;
            } else {
                carriages = null;
                carriagePlane = carriageFarPlane = carriageOutdated = null;
            }
        }

        /** The carriages a far-half pass draws: the map its selection (a lambda in Immersive Portals) holds. */
        Map<?, ?> captured(Object selection) throws ReflectiveOperationException {
            Class<?> type = selection.getClass();
            if (!capturedMaps.containsKey(type)) {
                Field found = null;
                for (Field f : type.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) found = open(f);
                }
                capturedMaps.put(type, found);
            }
            Field f = capturedMaps.get(type);
            return f == null ? null : (Map<?, ?>) f.get(selection);
        }

        private static <T extends java.lang.reflect.AccessibleObject> T open(T member) {
            member.setAccessible(true);
            return member;
        }
    }
}
