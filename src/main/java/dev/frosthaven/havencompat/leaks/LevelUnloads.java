package dev.frosthaven.havencompat.leaks;

import dev.frosthaven.havencompat.Log;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Immersive Portals' other client levels (the Nether and End a portal shows, kept for the session) are dropped at a world
 * exit ({@code ClientWorldLoader.cleanUp}) and at a dynamic dimension removal with no {@link LevelEvent.Unload}. Mods that
 * keep data per level and let go of it on that event kept every such level of every session: Create's Redstone Link
 * networks held the old Nether and End client levels after each rejoin (heap dump, 2026-11-22; also in a player's crash
 * log on 0.36.0). {@code mixin/IpLevelUnloadMixin} posts the event for each level that has not had one; the player's own
 * level gets vanilla's and is left alone.
 */
public final class LevelUnloads {
    private static final Log LOG = new Log("[level unloads] ");
    /** client levels that had their unload event, from anyone */
    private static final Set<ClientLevel> UNLOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private LevelUnloads() {}

    public static void register(IEventBus bus) {
        bus.addListener((LevelEvent.Unload e) -> { if (e.getLevel() instanceof ClientLevel level) UNLOADED.add(level); });
    }

    /** From the mixin: these levels are being dropped */
    public static void dropping(Iterable<ClientLevel> levels) {
        int n = 0;
        for (ClientLevel level : levels) {
            if (level == null || level == Minecraft.getInstance().level || UNLOADED.contains(level)) continue;
            try {
                NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
                n++;
            } catch (RuntimeException e) {
                LOG.error("unload", "a mod failed on the unload of a portal's client level " + level.dimension().location(), e);
            }
        }
        if (n > 0) LOG.once("posted", "Immersive Portals' other client levels get their unload event when it drops them ({} now)", n);
    }

    /** a map's values, for the mixin */
    public static void dropping(Map<?, ClientLevel> levels) {
        dropping(java.util.List.copyOf(levels.values()));
    }
}
