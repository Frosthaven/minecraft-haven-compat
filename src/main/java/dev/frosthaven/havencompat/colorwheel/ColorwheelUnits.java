package dev.frosthaven.havencompat.colorwheel;

import com.google.common.collect.ImmutableSet;
import dev.frosthaven.havencompat.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;

/**
 * Colorwheel keeps texture units for itself in every program: 7 for Flywheel's textures and, for its order-independent
 * transparency (OIT), 2 more and one per coefficient rank of the shader pack (Complementary: 2), 11 in all. The shader
 * pack's own samplers get what is left. Apple's driver has 16 units, and Colorwheel turns OIT off there but still keeps
 * its units: Complementary's translucent programs then had 5 for their own samplers, Iris threw "No more available
 * texture units while activating sampler shadowtex1", and Colorwheel dropped the program (fluid in glass pipes and
 * glass on contraptions not drawn, chat: "Current shader pack contains broken shaders").
 * <p>
 * On a driver with no more than 16 units, Colorwheel keeps a unit only for a program that reads it: the crumbling
 * texture, the two light textures and the OIT textures stay reserved where the linked program has their sampler, and
 * go to the shader pack's samplers elsewhere. That is safe because of how each one is bound: the light textures are
 * buffer textures (their own binding point on the unit, no sampler state), the crumbling texture is bound right before
 * its own programs (and {@link #bound} takes a shader pack's sampler object off its unit), and the OIT textures are
 * bound by each OIT step. The diffuse, overlay, lightmap and instance units are bound once per pass and always stay.
 * With more than 16 units nothing changes.
 */
public final class ColorwheelUnits {
    private static final Log LOG = new Log("[colorwheel units] ");
    private static final int SCARCE = 16;
    /** Unit -> the sampler uniform that reads it, for the units a program may give up; null when Colorwheel's units cannot be read. */
    private static Map<Integer, String> shared;
    private static int crumbling = -1, firstCoefficient = Integer.MAX_VALUE;
    private static Boolean scarce;
    /** The units of the program whose samplers Colorwheel is setting up now (render thread); null: all of them. */
    private static Set<Integer> current;

    private ColorwheelUnits() {}

    /** Colorwheel's reserved units for this linked program. */
    public static Set<Integer> reserved(int program, Set<Integer> units) {
        current = null;
        if (!scarce()) return units;
        ImmutableSet.Builder<Integer> kept = ImmutableSet.builder();
        for (int unit : units) {
            String sampler = unit >= firstCoefficient ? "clrwl_coefficients" + (unit - firstCoefficient) : shared.get(unit);
            if (sampler == null || GL20.glGetUniformLocation(program, sampler) >= 0) kept.add(unit);
        }
        current = kept.build();
        LOG.once("freed", "this graphics driver has {} texture units: Colorwheel keeps its crumbling, light and transparency units only in programs that read them, the shader pack's samplers get the rest",
                GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS));
        return current;
    }

    /** Colorwheel declares its sampler on this unit for the program being set up: only on a unit it kept (Iris refuses others). */
    public static boolean kept(int unit) {
        return current == null || current.contains(unit);
    }

    /** The program being set up reads the crumbling texture. */
    public static boolean crumbles() {
        return current != null && current.contains(crumbling);
    }

    /** A program that reads the crumbling texture is bound: a shader pack's sampler object left on that unit comes off. */
    public static void bound() {
        GL33.glBindSampler(crumbling, 0);
    }

    private static boolean scarce() {
        if (scarce == null) {
            scarce = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) <= SCARCE && readUnits();
        }
        return scarce;
    }

    /** {@code ClrwlSamplers}' unit numbers, by reflection: the unit type is a Flywheel class this mod does not compile against. */
    private static boolean readUnits() {
        try {
            Class<?> samplers = Class.forName("dev.djefrey.colorwheel.ClrwlSamplers");
            Map<Integer, String> map = new HashMap<>();
            crumbling = unit(samplers, "CRUMBLING");
            map.put(crumbling, "_flw_crumblingTex");
            map.put(unit(samplers, "LIGHT_LUT"), "_flw_lightLut");
            map.put(unit(samplers, "LIGHT_SECTIONS"), "_flw_lightSections");
            map.put(unit(samplers, "DEPTH_RANGE"), "_flw_depthRange");
            map.put(unit(samplers, "NOISE"), "_flw_blueNoise");
            firstCoefficient = unit(samplers, "FIRST_COEFFICIENT");
            shared = map;
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOG.error("unit", "could not read Colorwheel's texture units; it keeps all of them as before", e);
            return false;
        }
    }

    private static int unit(Class<?> samplers, String name) throws ReflectiveOperationException {
        Object unit = samplers.getField(name).get(null);
        return unit.getClass().getField("number").getInt(unit);
    }
}
