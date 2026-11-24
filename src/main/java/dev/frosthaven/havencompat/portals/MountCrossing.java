package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.Log;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client side. A mount crossing an Immersive Portals portal keeps its rider on the right side of the destination.
 * <p>
 * The game moves a mount that its rider steers and tells the server each tick; the server checks each move and, when it
 * disagrees ("moved wrongly", or a silent collision check), puts the mount back and sends the old position
 * ({@link ClientboundMoveVehiclePacket}). The move that carries a mount through a portal is often one of these: the server
 * checks it against blocks in the old dimension only. That packet has no dimension, and it comes after the client has
 * already taken player and mount through the portal (a round trip later; at Bisect's ping always). Vanilla applies it to
 * whatever the player rides: the mount in the new dimension got the old dimension's coordinates (the Nether drekki at
 * x -835, its Overworld x, 2026-11-24). The rider then hung at the plane or, when the next correction came, was carried
 * back over it: Immersive Portals took that as a crossing of the destination's reverse portal, then again, until its
 * combo limit ("Combo teleport out of limit") put the player back on one side, often the wrong one. On foot no such
 * packet exists (the player's position packet carries a dimension); {@link RefusedCrossing} covers the walking case.
 * <p>
 * Each client tick (and at each correction) the ridden mount's dimension and position are noted; a change of dimension or
 * a jump of more than {@value #JUMP} blocks is kept {@value #KEEP_MS} ms as a "before" spot. A correction that points to
 * within {@value MountCorrection#NEAR} blocks of the latest before spot, and is for another dimension or nearer that spot than the mount
 * now ({@link MountCorrection}), is one the server sent before it knew of the crossing: it is dropped ({@link #stale}), at most
 * {@value MountCorrection#MAX_DROPS} per crossing. Earlier spots are left out: after a run back through the portal the
 * server's real correction lay near the spot of the crossing before, and dropping every one held the mount for 3 s. The server learns of the
 * crossing from Immersive Portals' own teleport request and moves its mount with the player; its later corrections are
 * applied as usual.
 */
public final class MountCrossing {
    private static final Log LOG = new Log("[mount crossing] ");
    static final long KEEP_MS = 3_000;
    static final double JUMP = 3;
    private record Before(ResourceKey<Level> dim, Vec3 pos, long at) {}
    private static final ArrayDeque<Before> BEFORE = new ArrayDeque<>();
    private static int vehicle = -1;
    private static ResourceKey<Level> dim;
    private static Vec3 pos;
    private static int dropped, droppedThisCrossing;

    private MountCrossing() {}

    public static void register(IEventBus bus) {
        bus.addListener((ClientTickEvent.Post e) -> {
            var p = Minecraft.getInstance().player;
            Entity root = p == null ? null : p.getRootVehicle();
            if (root == null || root == p) { vehicle = -1; BEFORE.clear(); return; }
            note(root);
        });
    }

    private static void note(Entity v) {
        long now = System.currentTimeMillis();
        ResourceKey<Level> d = v.level().dimension();
        Vec3 at = v.position();
        if (v.getId() == vehicle && pos != null && (d != dim || at.distanceTo(pos) > JUMP)) { BEFORE.addLast(new Before(dim, pos, now)); droppedThisCrossing = 0; }
        vehicle = v.getId(); dim = d; pos = at;
        while (!BEFORE.isEmpty() && now - BEFORE.peekFirst().at > KEEP_MS) BEFORE.removeFirst();
    }

    /** From the mixin, on the client thread: true drops the correction. */
    public static boolean stale(ClientboundMoveVehiclePacket packet) {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        Entity root = p.getRootVehicle();
        if (root == p || !root.isControlledByLocalInstance()) return false;
        note(root);   // a crossing since the last tick
        double[] at = {packet.getX(), packet.getY(), packet.getZ()};
        double[] now = {root.getX(), root.getY(), root.getZ()};
        Before b = BEFORE.peekLast();   // the latest crossing only (MountCorrection.stale)
        if (b != null && MountCorrection.stale(at, new double[]{b.pos.x, b.pos.y, b.pos.z}, b.dim != root.level().dimension(), now, droppedThisCrossing)) {
            droppedThisCrossing++;
            if (dropped++ < 20) LOG.info("a correction of {} to {} {} came after its crossing to {} ({}): dropped",
                    root.getName().getString(), b.dim.location(), String.format("%.2f %.2f %.2f", at[0], at[1], at[2]), root.level().dimension().location(),
                    String.format("%.2f %.2f %.2f", now[0], now[1], now[2]));
            return true;
        }
        if (b != null && droppedThisCrossing == MountCorrection.MAX_DROPS && dropped++ < 20)
            LOG.info("{} corrections of {} after one crossing: the server holds it there, applied", MountCorrection.MAX_DROPS, root.getName().getString());
        return false;
    }
}
