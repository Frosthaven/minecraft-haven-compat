package dev.frosthaven.havencompat.contraptions;

import dev.frosthaven.havencompat.Log;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * Other players drew a player riding a Create elevator (or any contraption that moves up or down) in the falling or
 * jumping pose. Resource pack animations (Fresh Animations' player pack) use the on-ground flag, and the flag other
 * players get is the server's. On a moving floor that flag is false on most server ticks. A rider (in the last 2 seconds Create reported the
 * player touching a contraption, or the player moved up or down at a contraption's speed; and a contraption is within
 * 8 blocks) is sent to
 * other players as on the ground: always while that contraption moves up or down, and otherwise unless the player
 * rises like a jump. Only the sent flag changes (set at the start of ServerEntity.sendChanges, restored at its end):
 * the server's own state must stay, because a forced "on ground" makes vanilla count each upward movement update as a
 * jump and charge its hunger (0.30.7 to 0.31.0 did: about 5 saturation points per 175 blocks up at 256 RPM).
 */
public final class ContraptionGround {
    private static final Log LOG = new Log("[contraption riders] ");
    private static final int KEEP_TICKS = 40;
    private static final int ARRIVED_TICKS = 10;
    private static final double RISING = 0.35;   // a jump rises ~0.42 in its first tick
    private static final double MOVING = 0.01;   // blocks per tick, up or down
    private static final double MATCH = 0.15;    // a rider's y change per update next to the floor's per tick
    private static final double REACH = 8;       // with lag the server has the rider blocks behind a fast floor (0.5 blocks per tick at 256 RPM)
    private static final Map<UUID, Integer> TOUCHED = new ConcurrentHashMap<>();   // player -> server tick of Create's report
    private static final Map<UUID, Double> LAST_Y = new ConcurrentHashMap<>();     // player -> y at the previous movement update
    private static final Map<UUID, Double> RISE = new ConcurrentHashMap<>();       // player -> y change of the last movement update

    private ContraptionGround() {
    }

    /** At the end of Create's ClientMotionPacket handling: the player touches a contraption. */
    public static void reported(ServerPlayer player) {
        TOUCHED.put(player.getUUID(), player.server.getTickCount());
        LOG.once("reported", "Create reports players touching contraptions");
    }

    /** At the end of a movement update. */
    public static void afterMove(ServerPlayer player) {
        Double last = LAST_Y.put(player.getUUID(), player.getY());
        double rise = last == null ? 0 : player.getY() - last;
        RISE.put(player.getUUID(), rise);
        // Going up, Create's client carries the rider with the floor and reports no contact: moving with the floor counts.
        if (Math.abs(rise) > MOVING) {
            Double vy = floorSpeed(player, false);
            if (vy != null && Math.abs(vy) > MOVING && Math.abs(rise - vy) < MATCH) TOUCHED.put(player.getUUID(), player.server.getTickCount());
        }
    }

    /** True while the server must send this player to others as "on the ground" (ServerEntity.sendChanges). */
    public static boolean standsForOthers(ServerPlayer player) {
        if (player.onGround()) return false;
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger()) return false;
        Double vy = floorSpeed(player, true);
        if (vy == null) return false;
        if (Math.abs(vy) <= MOVING) {
            Double rise = RISE.get(player.getUUID());
            if (rise != null && rise > RISING) return false;
        }
        LOG.once("kept", "players standing on moving contraptions stay on the ground for other players");
        return true;
    }

    /**
     * Vertical speed (blocks per tick) of the contraption this player rides; null when the player rides none. A
     * contraption that just became blocks again (an elevator that arrived while the rider's updates are still on the
     * way) counts as a floor at rest for half a second.
     */
    private static Double floorSpeed(ServerPlayer player, boolean touchedOnly) {
        Integer tick = TOUCHED.get(player.getUUID());
        int age = tick == null ? Integer.MAX_VALUE : player.server.getTickCount() - tick;
        if (touchedOnly && age > KEEP_TICKS) return null;
        Double speed = null;
        for (AbstractContraptionEntity c : player.level().getEntitiesOfClass(AbstractContraptionEntity.class,
                player.getBoundingBox().inflate(REACH))) {
            double vy = c.getY() - c.yo;
            if (speed == null || Math.abs(vy) > Math.abs(speed)) speed = vy;
        }
        return speed == null && touchedOnly && age <= ARRIVED_TICKS ? Double.valueOf(0) : speed;
    }
}
