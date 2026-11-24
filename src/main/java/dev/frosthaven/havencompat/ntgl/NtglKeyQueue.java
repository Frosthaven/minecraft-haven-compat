package dev.frosthaven.havencompat.ntgl;

import dev.frosthaven.havencompat.Log;
import net.minecraft.client.KeyMapping;

/**
 * NTGL 3.2.0's {@code InputHandler.handleKeys()} reads its key presses (attachments, inspect, fire select, tips, debug
 * show) with {@code consumeClick()} only when the hand it picks (off hand while sneaking, else main hand) holds a
 * weapon. Otherwise a press stays queued and fires later: sneak + ";" with a tank in the off hand changed the tank
 * (Mekanism) and, after sneaking ended, the main-hand gun's fire mode too. At the end of each {@code handleKeys} the
 * left-over clicks of those keys are dropped, so a press never outlives its tick. Keys by reflection
 * ({@code NtglKeyBinds} static fields).
 */
public final class NtglKeyQueue {
    private static final Log LOG = new Log("[ntgl key queue] ");
    private static final String[] KEYS = {"KEY_ATTACHMENTS", "KEY_DEBUG_SHOW", "KEY_INSPECT", "KEY_FIRE_SELECT", "KEY_TIPS"};
    private static KeyMapping[] keys;
    private static boolean broken;

    private NtglKeyQueue() {}

    public static void drain() {
        if (broken) return;
        try {
            if (keys == null) {
                Class<?> c = Class.forName("com.nukateam.ntgl.client.input.NtglKeyBinds");
                KeyMapping[] found = new KeyMapping[KEYS.length];
                for (int i = 0; i < KEYS.length; i++) found[i] = (KeyMapping) c.getField(KEYS[i]).get(null);
                keys = found;
            }
            for (KeyMapping k : keys) {
                if (k == null) continue;
                boolean dropped = false;
                while (k.consumeClick()) dropped = true;
                if (dropped) LOG.once("on", "a press of NTGL's {} with no weapon in the chosen hand was dropped, not kept for later", k.getName());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            LOG.error("reflect", "cannot reach NTGL's key mappings; its queued presses stay", e);
        }
    }
}
