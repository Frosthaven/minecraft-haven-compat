package dev.frosthaven.havencompat.carryon;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Client side. Carry On's carry-data sync moves the hotbar selection only while the player carries something.
 * <p>
 * {@code ClientboundSyncCarryDataPacket.handle} sets {@code Inventory.selected} to the slot in the carry data on every
 * sync, carrying or not. Carry On sends that sync when the player changes dimension, and a player carrying nothing has
 * slot 0 there: after every portal crossing the selection jumped to the first slot (Shane, 2026-11-26; traintest
 * slots-1..5: 6 of 13 crossings at 100 ms latency, a JDWP field watch named this handler for all 6 jumps). Carry On
 * keeps the slot of the carried block or entity locked while carrying, which is what this write is for, so that case
 * stays as it was.
 */
public final class CarrySlot {
    private static final Log LOG = new Log("[carry slot] ");
    private static Field data;
    private static Method carrying;
    private static boolean broken;
    private static int kept;

    private CarrySlot() {}

    /** true: the sync may set the selection (the packet's carry data says the player carries something) */
    public static boolean allowed(Object packet, int slot, int now) {
        if (broken) return true;
        try {
            if (data == null) { data = packet.getClass().getDeclaredField("data"); data.setAccessible(true); }
            Object d = data.get(packet);
            if (carrying == null) carrying = d.getClass().getMethod("isCarrying");
            boolean carries = (Boolean) carrying.invoke(d);
            if (!carries && slot != now && kept++ < 5) LOG.info("Carry On's sync would move the hotbar selection from {} to {} with nothing carried: kept at {}", now, slot, now);
            return carries;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOG.info("Carry On's carry data is not readable ({}): its sync sets the selection as before", e.toString());
            return true;
        }
    }
}
