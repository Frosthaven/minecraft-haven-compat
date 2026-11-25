package dev.frosthaven.havencompat.contraptions;

import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Other players saw a seated rider on a Create train drift behind its seat and snap back (live server, 2026-11-25). The
 * game draws the carriage at lerp(its previous position, its position) and the rider at lerp(the rider's previous
 * position, the rider's position): two histories that do not agree. The rider's previous position is a tick behind or
 * stale (a portal crossing, Create's own rewrites of the carriage's), so the rider lagged (85 to 90 % of the carriage's
 * movement per frame after a crossing) and jumped up to 1.6 blocks in one frame while the carriage stood still
 * (traintest rig, the driver's view of the passenger, the same at localhost and on a Bisect-like line).
 *
 * A rider of a train carriage or other oriented contraption (minecart contraptions) is drawn at the carriage's drawn
 * position plus its seat offset (rider minus carriage) sampled once per client tick (game time) and interpolated between the last tick and this one. That follows
 * turns and ramps alike and never reads the rider's own previous position. An offset change over 1 block in one tick
 * is a real move (another seat, a teleport) and is drawn at once. Only the drawn position changes (every render pass:
 * world, shader shadow, portal views); the rider's real position, the camera of the rider's own game and the server
 * are untouched. A first version turned this tick's offset by the carriage's yaw only and still stepped on ramps.
 */
public final class SeatDraw {
    private static final Log LOG = new Log("[seat draw] ");

    private SeatDraw() {
    }

    private static final double MOVED = 1.0;
    private static final java.util.Map<Entity, Seat> SEATS = new java.util.WeakHashMap<>();   // render thread only

    private static final class Seat {
        int vehicle;
        long tick;
        Vec3 prev, cur;
    }

    /** What to add to the position the game draws {@code entity} at this frame. */
    public static Vec3 shift(Entity entity, float partialTick) {
        if (!(entity.getVehicle() instanceof OrientedContraptionEntity carriage)) return Vec3.ZERO;
        if (entity == Minecraft.getInstance().getCameraEntity() || entity.level() != carriage.level()) return Vec3.ZERO;
        Vec3 now = entity.position().subtract(carriage.position());
        long time = carriage.level().getGameTime();
        Seat seat = SEATS.get(entity);
        if (seat == null || seat.vehicle != carriage.getId()) {
            seat = new Seat(); seat.vehicle = carriage.getId(); seat.tick = time; seat.prev = now; seat.cur = now;
            SEATS.put(entity, seat);
        } else if (seat.tick != time) {   // one sample per client tick (a passenger's own tickCount is no clock)
            seat.tick = time; seat.prev = seat.cur; seat.cur = now;
            if (seat.prev.distanceTo(now) > MOVED) seat.prev = now;
        }
        LOG.once("on", "riders on Create trains and contraption carts are drawn on their seat");
        double x = Mth.lerp(partialTick, carriage.xOld, carriage.getX()) + Mth.lerp(partialTick, seat.prev.x, seat.cur.x);
        double y = Mth.lerp(partialTick, carriage.yOld, carriage.getY()) + Mth.lerp(partialTick, seat.prev.y, seat.cur.y);
        double z = Mth.lerp(partialTick, carriage.zOld, carriage.getZ()) + Mth.lerp(partialTick, seat.prev.z, seat.cur.z);
        return new Vec3(x - Mth.lerp(partialTick, entity.xOld, entity.getX()), y - Mth.lerp(partialTick, entity.yOld, entity.getY()),
                z - Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }
}
