package dev.frosthaven.havencompat.colorwheel;

import com.google.common.collect.ImmutableSet;
import dev.djefrey.colorwheel.util.GlCompat;
import dev.frosthaven.havencompat.Log;

/**
 * Colorwheel keeps texture units for itself in every program: 7 for Flywheel's textures and, for its order-independent
 * transparency (OIT), 2 more and one per coefficient rank of the shader pack (Complementary: 2). The shader pack's own
 * samplers get what is left. Colorwheel turns OIT off on Apple's driver ({@code GlCompat.SUPPORTS_OIT}) but still keeps
 * those units, and Apple has 16 units in all: Complementary's translucent program then had 5 for its own samplers, Iris
 * threw "No more available texture units while activating sampler shadowtex1", and Colorwheel dropped the program
 * (translucent Create parts not drawn, chat: "Current shader pack contains broken shaders").
 * <p>
 * Where Colorwheel says OIT is off, nothing binds or reads the OIT units, so they go back to the shader pack. With OIT
 * supported nothing changes.
 */
public final class ColorwheelUnits {
    private static final Log LOG = new Log("[colorwheel units] ");
    /** Colorwheel's first OIT unit; its units before that are Flywheel's. Above every unit when it cannot be read: nothing changes. */
    private static final int FIRST_OIT_UNIT = firstOitUnit();

    private ColorwheelUnits() {}

    /** Colorwheel's reserved units for a program: without the OIT ones where OIT is off. */
    public static ImmutableSet<Integer> reserved(ImmutableSet<Integer> units) {
        if (GlCompat.SUPPORTS_OIT) return units;
        ImmutableSet<Integer> kept = units.stream().filter(ColorwheelUnits::used).collect(ImmutableSet.toImmutableSet());
        LOG.once("freed", "Colorwheel's transparency (OIT) is off on this graphics driver: the texture units it keeps for it go to the shader pack's samplers");
        return kept;
    }

    /** {@code ClrwlSamplers.DEPTH_RANGE.number}, by reflection: the unit's type is a Flywheel class this mod does not compile against. */
    private static int firstOitUnit() {
        try {
            Object unit = Class.forName("dev.djefrey.colorwheel.ClrwlSamplers").getField("DEPTH_RANGE").get(null);
            return unit.getClass().getField("number").getInt(unit);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOG.error("unit", "could not read Colorwheel's texture units; it keeps its transparency units as before", e);
            return Integer.MAX_VALUE;
        }
    }

    /** Colorwheel uses this one of its texture units on this driver. */
    public static boolean used(int unit) {
        return GlCompat.SUPPORTS_OIT || unit < FIRST_OIT_UNIT;
    }
}
