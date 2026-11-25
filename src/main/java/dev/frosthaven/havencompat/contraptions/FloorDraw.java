package dev.frosthaven.havencompat.contraptions;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.TranslatingContraption;
import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * Other players saw a player standing on a Create elevator duck and stand up over and over (live server, 2026-11-25).
 * The cabin moves smoothly in each game, but the other player's position arrives late and uneven from the server, so
 * on the way up the cabin floor rises past the drawn feet and the model sinks into it, then pops up as updates land
 * (traintest rig, a Bisect-like line: drawn up to 9 blocks below the floor on a 1 block per tick pulley, the server's
 * position on the floor all the way; Create's own remote-player guard searches only a few blocks). On the way down the
 * lag holds the player above the floor, where the cabin carries them.
 *
 * Another player (not riding, not the camera) whom the server reports on the ground (haven-compat's ContraptionGround
 * keeps that true on a moving contraption), standing inside a moving lift (a translating contraption: pulley, piston,
 * gantry; not a train or cart) is drawn at the lift's floor nearest to them, at the lift's drawn height. Only the drawn
 * height changes.
 */
public final class FloorDraw {
    private static final Log LOG = new Log("[lift floors] ");
    private static final int REACH = 12;   // blocks searched above and below the drawn feet: the lag is mostly below on the way up (7 measured)
    private static final double MOVING = 0.01, MOST = 12;

    private FloorDraw() {
    }

    /** What to add to the position the game draws {@code entity} at this frame. */
    public static Vec3 shift(Entity entity, float partialTick) {
        if (!(entity instanceof Player player) || player.isPassenger() || !player.onGround() || entity == Minecraft.getInstance().getCameraEntity()) return Vec3.ZERO;
        for (AbstractContraptionEntity lift : player.level().getEntitiesOfClass(AbstractContraptionEntity.class, player.getBoundingBox().inflate(1, REACH, 1))) {
            if (lift instanceof OrientedContraptionEntity || !(lift.getContraption() instanceof TranslatingContraption)) continue;
            if (Math.abs(lift.getY() - lift.yOld) < MOVING) continue;
            Vec3 local = lift.toLocalVector(player.position(), 1f);
            BlockPos column = BlockPos.containing(local);
            double floor = Double.NaN;
            for (int dy = -REACH; dy <= REACH; dy++) {
                BlockPos at = column.offset(0, dy, 0);
                double top = top(lift, at);
                if (Double.isNaN(top) || !Double.isNaN(top(lift, at.above())) || !Double.isNaN(top(lift, at.above(2)))) continue;
                if (Double.isNaN(floor) || Math.abs(top - local.y) < Math.abs(floor - local.y)) floor = top;
            }
            if (Double.isNaN(floor)) continue;
            double now = lift.toGlobalVector(new Vec3(local.x, floor, local.z), 1f).y;
            double drawn = now + Mth.lerp(partialTick, lift.yOld, lift.getY()) - lift.getY();
            double dy = drawn - Mth.lerp(partialTick, player.yOld, player.getY());
            if (Math.abs(dy) > MOST) continue;
            LOG.once("on", "players standing on moving lifts are drawn on their floor");
            return new Vec3(0, dy, 0);
        }
        return Vec3.ZERO;
    }

    /** the top of the lift block's collision at local {@code pos}, NaN when there is none */
    private static double top(AbstractContraptionEntity lift, BlockPos pos) {
        var info = lift.getContraption().getBlocks().get(pos);
        if (info == null) return Double.NaN;
        var shape = info.state().getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
        return shape.isEmpty() ? Double.NaN : pos.getY() + shape.max(Direction.Axis.Y);
    }
}
