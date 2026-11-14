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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

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
 * Not covered: train carriages in a portal (Immersive Portals' plane for those takes Flywheel's environment type).
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
    /** Colorwheel's indirect backend uses shader storage bindings 0 to 9. */
    static final int BINDING = 10;
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
    private static Handles handles;

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
            Collection<?> environments = (Collection<?>) handles.environments.get(handles.storage.invoke(engine));
            boolean straddling = far || !(boolean) handles.sessionsEmpty.invoke(null);
            int maxIndex = 0;
            for (Object o : environments) {
                Env env = (Env) o;
                if (env.haven_compat$deleted()) continue;
                maxIndex = Math.max(maxIndex, env.haven_compat$index());
                if (!straddling) continue;
                Vec3i origin = ((Env) root(o)).haven_compat$origin();
                float[] plane;
                if (far) {
                    plane = ship != null && ofShip(level, origin, ship) ? (float[]) handles.farPlane.invoke(null, session, renderOrigin) : null;
                } else {
                    plane = (float[]) handles.nearPlane.invoke(null, level, origin, renderOrigin);
                }
                if (plane != null) PLANES.put(o, plane);
            }
            boolean cut = far || !PLANES.isEmpty();
            // The indirect shaders read this buffer on every embedded draw, so it always holds an entry per environment.
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
            if (buffer != 0) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, buffer);
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

    /** Reflection, resolved once on the first pass. */
    private static final class Handles {
        final Method storage, sessionsEmpty, nearPlane, farPlane, farShip, farSession, container, plot, plotShip;
        final Field environments, parent, farHalf;
        final Class<?> shipFarHalf;

        Handles(Object engine) throws ReflectiveOperationException {
            storage = engine.getClass().getMethod("environmentStorage");
            environments = open(Class.forName("dev.djefrey.colorwheel.engine.embed.EnvironmentStorage").getDeclaredField("environments"));
            parent = open(Class.forName(ENVIRONMENT).getDeclaredField("parent"));
            Class<?> straddle = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPFlywheelStraddleClip");
            farHalf = open(straddle.getDeclaredField("farHalf"));
            shipFarHalf = Class.forName(straddle.getName() + "$ShipFarHalf");
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
        }

        private static <T extends java.lang.reflect.AccessibleObject> T open(T member) {
            member.setAccessible(true);
            return member;
        }
    }
}
