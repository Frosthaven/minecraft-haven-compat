package dev.frosthaven.havencompat.megatorch;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Torchmaster 21.1 read by reflection: {@code Torchmaster.getRegistryForLevel(level)} is a dimension's saved list of
 * every Mega Torch and Dread Lamp (loaded chunk or not; a placed block registers itself in {@code onPlace}), held in
 * the manager's private {@code lights} map (key -> light). Its public {@code getEntries()} returns an empty array in
 * 21.1.13, so the map is read directly; on the server thread, which is the only one that changes it.
 */
final class TorchmasterLights {
    private static final String MEGATORCH = "net.xalcon.torchmaster.logic.entityblocking.megatorch.MegatorchEntityBlockingLight";
    private static Method registry, pos, config, radius;
    private static java.lang.reflect.Field lights;
    private static boolean ready, failed;

    private TorchmasterLights() {}

    /** The packed positions of the dimension's Mega Torches, or null when Torchmaster cannot be read. */
    static LongOpenHashSet megatorches(Level level) {
        if (!init()) return null;
        try {
            Object manager = ((Optional<?>) registry.invoke(null, level)).orElse(null);
            LongOpenHashSet out = new LongOpenHashSet();
            if (manager == null) return out;
            for (Object light : ((java.util.Map<?, ?>) lights.get(manager)).values()) {
                if (light != null && light.getClass().getName().equals(MEGATORCH)) out.add(((BlockPos) pos.invoke(light)).asLong());
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    /** The Mega Torch radius from Torchmaster's config (blocks each way from the torch). */
    static int radius() {
        if (!init()) return 64;
        try {
            return (int) radius.invoke(config.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return 64;
        }
    }

    private static boolean init() {
        if (ready || failed) return ready;
        try {
            Class<?> torchmaster = Class.forName("net.xalcon.torchmaster.Torchmaster");
            Class<?> manager = Class.forName("net.xalcon.torchmaster.logic.entityblocking.FilteredLightManager");
            Class<?> light = Class.forName("net.xalcon.torchmaster.logic.entityblocking.IEntityBlockingLight");
            registry = torchmaster.getMethod("getRegistryForLevel", Level.class);
            config = torchmaster.getMethod("getConfig");
            radius = config.getReturnType().getMethod("getMegaTorchRadius");
            lights = manager.getDeclaredField("lights");
            lights.setAccessible(true);
            pos = light.getMethod("getPos");
            ready = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
        return ready;
    }

    private static void fail(Exception e) {
        failed = true;
        ready = false;
        MegatorchAreas.LOG.error("read", "Torchmaster's torch list could not be read; Mega Torch areas stay off the map", e);
    }
}
