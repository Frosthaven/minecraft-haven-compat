package dev.frosthaven.havencompat.portals;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Server side. A train seat that enters the wall a portal is set in does not throw its rider off while the carriage
 * crosses that portal.
 * <p>
 * Create's seats on moving contraptions ({@code SeatMovementBehaviour.visitNewPosition}) take the rider off and put
 * them at the block's centre when the seat moves into a block that occludes (or a bottom slab). A train portal set into a
 * wall or a cliff has such blocks right behind its plane, on the old side. The carriage copy on the old side still
 * carries the seats that are past the plane until Immersive Portals hands their riders to the far copy
 * ({@code IPCreateRiderHandOver}); a seat ahead of the driver's met the wall first, and its rider was put down behind
 * the portal on the old side, never seated again (the drill portal at x -1088, z 1274, 2026-11-24: the passenger on
 * the front seat, every return run; found with the traintest rig's dismount stack, "SeatMovementBehaviour.visitNewPosition").
 * <p>
 * Here: on a carriage, a seat that would eject is left alone when its block is past the plane of the carriage's
 * crossing ({@code IPCreateCarriageCrossing.of}, {@code Crossing.isBeyond}) or within {@value #NEAR} blocks of a portal
 * (the crossing is not always set up yet when the front seat meets the wall); the hand-over then moves its rider
 * through as it does the driver.
 */
public final class SeatsAtPortals {
    private static final Log LOG = new Log("[portal seats] ");
    private static Method of, isBeyond;
    private static boolean missing;
    private static int logged;

    private SeatsAtPortals() {}

    /** From the mixin: true keeps the rider seated. */
    public static boolean keep(MovementContext ctx, BlockPos pos) {
        if (ctx.world == null || ctx.world.isClientSide() || ctx.contraption == null) return false;
        if (!(ctx.contraption.entity instanceof CarriageContraptionEntity cce)) return false;
        var state = ctx.world.getBlockState(pos);
        boolean ejects = state.canOcclude() || state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                && state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM;
        if (!ejects) return false;   // Create would not eject here: nothing to decide
        Boolean beyond = beyondCrossing(cce, pos);
        boolean nearPortal = nearPortal(ctx.world, pos);
        boolean keep = Boolean.TRUE.equals(beyond) || nearPortal;
        if (logged++ < 40) LOG.info("a seat of carriage {} entered {} at {} ({}): crossing {}, portal within {} blocks {}: {}", cce.getId(),
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos.toShortString(), ctx.world.dimension().location(),
                beyond == null ? "none" : beyond ? "beyond the plane" : "this side", NEAR, nearPortal, keep ? "rider kept for the hand-over" : "Create ejects");
        return keep;
    }

    static final double NEAR = 4;
    private static Class<?> portalClass;

    /** An Immersive Portals portal (its entity) within NEAR blocks of the block. */
    private static boolean nearPortal(net.minecraft.world.level.Level level, BlockPos pos) {
        try {
            if (portalClass == null) portalClass = Class.forName("qouteall.imm_ptl.core.portal.Portal");
        } catch (ClassNotFoundException e) { return false; }
        var box = new net.minecraft.world.phys.AABB(pos).inflate(NEAR);
        return !level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, portalClass::isInstance).isEmpty();
    }

    /** The carriage's crossing (Immersive Portals): true when the block is past the plane, null without a crossing. */
    private static Boolean beyondCrossing(CarriageContraptionEntity cce, BlockPos pos) {
        if (missing) return null;
        Carriage.DimensionalCarriageEntity copy = cce.getCarriage().getDimensionalIfPresent(cce.level().dimension());
        if (copy == null) return null;
        try {
            if (of == null) {
                Class<?> c = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPCreateCarriageCrossing");
                of = c.getMethod("of", CarriageContraptionEntity.class, Carriage.DimensionalCarriageEntity.class);
                isBeyond = Class.forName("qouteall.imm_ptl.core.compat.create_integration.IPCreateCarriageCrossing$Crossing").getMethod("isBeyond", Vec3.class);
            }
            Object crossing = of.invoke(null, cce, copy);
            return crossing == null ? null : (Boolean) isBeyond.invoke(crossing, Vec3.atCenterOf(pos));
        } catch (ReflectiveOperationException | RuntimeException e) {
            missing = true;
            LOG.info("Immersive Portals' carriage crossing is not readable ({}): only the portal distance is used", e.toString());
            return null;
        }
    }
}
