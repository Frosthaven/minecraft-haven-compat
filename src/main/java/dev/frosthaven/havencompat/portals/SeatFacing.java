package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client. A player seated on a train keeps facing the same way against the carriage through a portal. Create takes
 * riders off at a crossing and seats them on the carriage on the far side (and {@link LatePassengers} seats this
 * player again when the game drops the seat); the player kept the facing from before while the carriage on the far side
 * can point another way. Riding from the coal drill into the Nether turned Shane a quarter turn on a forward seat
 * (2026-11-27). On a new seat within 3 seconds of leaving one, the facing against the carriage comes back when it moved
 * more than 20 degrees. Logged as "[seat facing]".
 */
public final class SeatFacing {
    private static final Log LOG = new Log("[seat facing] ");
    private static final long KEEP_MS = 3000;
    private static float rel;
    private static long leftAt;
    private static Entity seat;

    private SeatFacing() {}

    public static void register(IEventBus bus) { bus.addListener(ClientTickEvent.Post.class, e -> tick()); }

    private static boolean carriage(Entity v) {
        return v != null && v.getClass().getName().equals("com.simibubi.create.content.trains.entity.CarriageContraptionEntity");
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { seat = null; return; }
        Entity v = mc.player.getVehicle();
        long now = System.currentTimeMillis();
        if (!carriage(v)) {
            if (seat != null) { leftAt = now; seat = null; }
            return;
        }
        float r = Mth.wrapDegrees(mc.player.getYRot() - v.getYRot());
        if (v != seat) {
            if (leftAt != 0 && now - leftAt < KEEP_MS && Math.abs(Mth.wrapDegrees(r - rel)) > 20) {
                float yaw = v.getYRot() + rel;
                LOG.info("seated again on the far side facing {} degrees off; turned back to the facing against the train", Math.round(Mth.wrapDegrees(r - rel)));
                mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setYHeadRot(yaw);
                r = rel;
            }
            seat = v; leftAt = 0;
        }
        rel = r;
    }
}
