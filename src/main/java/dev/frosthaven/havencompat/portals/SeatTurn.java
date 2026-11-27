package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Client. Create turns a seated player by each frame's turn of the carriage's view yaw
 * (ContraptionPlayerPassengerRotation). After a portal the client's copy of a carriage first shows a wrong yaw and
 * then swings 90 to 180 degrees in a tick or two, and Create turned the player with it: riding the L&S Courier Line
 * from the coal drill into the Nether, Shane looked a quarter turn off (2026-11-27; reproduced in a copy of the live
 * world: a rider went -180, 0, -180, -302, -360 within a second while the train ran straight). A train cannot turn
 * that fast, so a step of more than {@link #DEG_PER_TICK} degrees per tick of elapsed time (plus {@link #SLACK}) is
 * not passed on to the player. Replaces the 0.46.0/0.47.0 seat-facing guard, which measured against the same unsettled
 * yaw and turned players itself. Logged as "[seat turn]" (first few times).
 */
public final class SeatTurn {
    private static final Log LOG = new Log("[seat turn] ");
    /** a carriage on Create's tightest curve at top speed turns about 15 degrees per tick */
    static final float DEG_PER_TICK = 45, SLACK = 5;
    private static long last;
    private static int logged;

    private SeatTurn() {}

    /** called for Create's turn of the seated player to {@code yaw} */
    public static void turn(Player p, float yaw) {
        long now = System.nanoTime();
        double ticks = last == 0 ? 1 : Math.min(20, (now - last) / 50_000_000.0);
        last = now;
        float step = Mth.wrapDegrees(yaw - p.getYRot());
        if (Math.abs(step) > DEG_PER_TICK * ticks + SLACK) {
            if (logged++ < 20) LOG.info("the carriage turned {} degrees in {} ticks (no train turns that fast): the player keeps facing",
                Math.round(step), String.format("%.2f", ticks));
            return;
        }
        p.setYRot(yaw);
    }
}
