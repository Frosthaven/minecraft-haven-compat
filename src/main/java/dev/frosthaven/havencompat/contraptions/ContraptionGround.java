package dev.frosthaven.havencompat.contraptions;

import dev.frosthaven.havencompat.Log;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Other players drew a player riding a Create elevator (or any contraption going down) in the falling pose. Resource
 * pack animations (Fresh Animations' player pack) fall when a player is not on the ground and goes down, and the
 * on-ground flag other players get is the server's. While a contraption goes down, both the rider's movement update
 * and Create's own report ({@code ClientMotionPacket}, sent while the rider touches a contraption, with the on-ground
 * flag of that moment) say "in the air" on some ticks: the floor dropped first. A player Create reported touching a
 * contraption within the last 10 ticks (its reports skip ticks), who is not going up (a jump stays a jump), flying, gliding or riding, now stays
 * on the ground on the server: after each of those updates and right before the server sends the player to others
 * (something in the tick, Create's server-side contraption collision, can clear it again).
 */
public final class ContraptionGround {
    private static final Log LOG = new Log("[contraption riders] ");
    private static final int KEEP_TICKS = 10;
    private static final double RISING = 0.35;   // a jump rises ~0.42 in its first tick; a contraption nudging its rider up, less
    private static final Map<UUID, Integer> TOUCHED = new ConcurrentHashMap<>();   // player -> server tick of Create's report
    private static final Map<UUID, Double> LAST_Y = new ConcurrentHashMap<>();     // player -> y at the previous movement update

    private ContraptionGround() {
    }

    /** At the end of Create's ClientMotionPacket handling: the player touches a contraption. */
    public static void reported(ServerPlayer player) {
        TOUCHED.put(player.getUUID(), player.server.getTickCount());
        LOG.once("reported", "Create reports players touching contraptions");
        keep(player);
    }

    /** At the end of a movement update. */
    public static void afterMove(ServerPlayer player) {
        keep(player);
        LAST_Y.put(player.getUUID(), player.getY());
    }

    /** Right before the server sends the player's state to other players (ServerEntity.sendChanges). */
    public static void beforeBroadcast(ServerPlayer player) {
        keep(player);
    }

    private static void keep(ServerPlayer player) {
        if (player.onGround()) return;
        Integer tick = TOUCHED.get(player.getUUID());
        if (tick == null || player.server.getTickCount() - tick > KEEP_TICKS) return;
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger()) return;
        Double lastY = LAST_Y.get(player.getUUID());
        if (lastY != null && player.getY() > lastY + RISING) return;
        player.setOnGround(true);
        LOG.once("kept", "players standing on moving contraptions stay on the ground for other players");
    }
}
