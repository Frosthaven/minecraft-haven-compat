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
 * can point another way. Create turns a seated player with the carriage's view yaw each frame, but on a new carriage
 * entity it starts over from that carriage (no turn for the step between the two): riding from the coal drill into the
 * Nether turned Shane a quarter turn (2026-11-27). The facing here is against that view yaw, as Create measures it. On a new carriage, while riding or within 3 seconds of the last, the facing against the carriage is
 * held for half a second: a turn of more than 45 degrees in that time is put back. Logged as "[seat facing]".
 */
public final class SeatFacing {
    private static final Log LOG = new Log("[seat facing] ");
    private static final long KEEP_MS = 3000;
    /** ticks on a new carriage the facing is kept: a turn can come a few ticks after the seat */
    private static final int HOLD = 10;
    private static float rel, keep;
    private static long ridAt;
    private static Entity seat;
    private static int hold;

    private SeatFacing() {}

    public static void register(IEventBus bus) { bus.addListener(ClientTickEvent.Post.class, e -> tick()); }

    private static boolean carriage(Entity v) { return v instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity; }

    /** the yaw a player faces along the train, as Create turns a seated player (ContraptionPlayerPassengerRotation:
     *  the carriage's view yaw, the player aligned with it at -view - 90) */
    private static float along(Entity v) {
        return -((com.simibubi.create.content.trains.entity.CarriageContraptionEntity) v).getViewYRot(1f) - 90;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { seat = null; return; }
        Entity v = mc.player.getVehicle();
        long now = System.currentTimeMillis();
        if (!carriage(v)) { seat = null; hold = 0; return; }
        float r = Mth.wrapDegrees(mc.player.getYRot() - along(v));
        if (v != seat) {
            /* a new carriage while riding, or within 3 s of the last: at a portal the game moves the player straight from
               the old carriage to the new one (no tick off a seat; the 0.46.0 check waited for one and never ran) */
            if (ridAt != 0 && now - ridAt < KEEP_MS) { keep = rel; hold = HOLD; }
            seat = v;
        }
        if (hold > 0) {
            hold--;
            float off = Mth.wrapDegrees(r - keep);
            /* a portal turns in quarter turns: more than 45 degrees against the train in the first ticks is the portal's,
               not the player's mouse */
            if (Math.abs(off) > 45) {
                float yaw = along(v) + keep;
                LOG.info("on a new carriage facing {} degrees off; turned back to the facing against the train", Math.round(off));
                mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setYHeadRot(yaw);
                r = keep;
            }
        }
        rel = r; ridAt = now;
    }
}
