package dev.frosthaven.havencompat.leaks;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * NTGL's {@code PlayerEventHandler.lastSelectedSlots} (a static map keyed by hand and player, filled at every client
 * player tick) is never emptied: it kept every player the client ever saw, the old session's own player too, and that
 * player's client level with it (heap dump after a rejoin, 2026-11-22). A player's entries go when it leaves the client
 * level; all of them at a logout.
 */
public final class NtglPlayers {
    private static final Log LOG = new Log("[ntgl players] ");
    private static Map<?, ?> slots;
    private static boolean broken;

    private NtglPlayers() {}

    public static void register(IEventBus bus) {
        bus.addListener((EntityLeaveLevelEvent e) -> {
            if (e.getLevel().isClientSide && e.getEntity() instanceof Player p) forget(p);
        });
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> { Map<?, ?> m = slots(); if (m != null) m.clear(); });
    }

    private static void forget(Player player) {
        Map<?, ?> m = slots();
        if (m != null) m.keySet().removeIf(k -> k instanceof com.mojang.datafixers.util.Pair<?, ?> pair && pair.getSecond() == player);
    }

    private static Map<?, ?> slots() {
        if (slots != null || broken) return slots;
        try {
            Field f = Class.forName("com.nukateam.ntgl.common.handlers.PlayerEventHandler").getDeclaredField("lastSelectedSlots");
            f.setAccessible(true);
            slots = (Map<?, ?>) f.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            LOG.warnOnce("field", "NTGL's player slot map is not where it was; its old players are kept as before: {}", e.toString());
        }
        return slots;
    }
}
