package dev.frosthaven.havencompat.contraptions;

import dev.frosthaven.havencompat.Log;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;

/**
 * Server side. The vanilla spawn packet carries {@code ServerEntity}'s last sent position, which moves only every
 * update interval; a Create contraption that moved since then spawned on a client where it no longer was. The packet
 * now carries the entity's position now. Clients need nothing: the server alone fixes it for every player.
 *
 * <p>Train carriages (update interval 3) stayed invisible on a client until they left view and came back (Create
 * issue 10660; at Haven's Village Underground station 5 of 30 arrivals at normal settings, 2026-11-03). The server
 * starts sending an entity once the chunk it is in now is in the player's view ({@code ChunkMap.TrackedEntity.updatePlayer}),
 * so a carriage that just crossed into the player's outermost chunk spawned up to 2 ticks of travel behind, in a chunk
 * the client never received. The client does not tick an entity there, and a carriage moves and becomes renderable only
 * in its own client tick ({@code CarriageContraptionEntity.tickContraption}); it also skips server move and teleport
 * packets for it ({@code isControlledByLocalInstance()} is true). So it stayed frozen there.
 *
 * <p>Pulley, piston and bearing contraptions, elevators included ({@code ControlledContraptionEntity}, update interval
 * 40), vanished for good on one player's client while others saw them (2026-11-11). Its spawn data carries the
 * controller as an offset from the server's {@code blockPosition()} now ({@code ControllerRelative}); the client adds
 * that offset to the block of the spawn position. After a move of up to 2 seconds the two differ, the client looks for
 * the pulley at the wrong block, finds none, and {@code tickContraption} discards the entity on that client. The server
 * keeps sending it moves for an id the client no longer has, until the player leaves its range or joins again.
 */
public final class ContraptionSpawn {
    /** Create: a train carriage, and a contraption a block moves (pulley, elevator, piston, bearing). */
    public static final String CARRIAGE = "com.simibubi.create.content.trains.entity.CarriageContraptionEntity";
    public static final String CONTROLLED = "com.simibubi.create.content.contraptions.ControlledContraptionEntity";
    private static final Log LOG = new Log("[contraptions] ");

    private ContraptionSpawn() {
    }

    /** The spawn packet with the contraption's position now (vanilla's uses the last position sent). */
    public static Packet<ClientGamePacketListener> packet(Entity contraption) {
        LOG.once("on", "train carriages and moved contraptions (elevators, pulleys, pistons, bearings) spawn on clients at their real position");
        return new ClientboundAddEntityPacket(contraption.getId(), contraption.getUUID(), contraption.getX(), contraption.getY(), contraption.getZ(),
                contraption.getXRot(), contraption.getYRot(), contraption.getType(), 0, contraption.getDeltaMovement(), contraption.getYHeadRot());
    }

    /**
     * A rider of a contraption (a mob on a train seat): its spawn packet at its position now. The vanilla packet carries
     * the position the server last sent for it, and a seated rider's position is never sent (the client places it on its
     * seat). Create adds a carriage's riders again from their saved data when the carriage comes into loaded ground or out
     * of a portal, at the spot they were saved at, then seats them; a player at the station got the rider there, in a
     * chunk that player's game did not have. A game draws and ticks only entities in its loaded chunks, and a rider moves
     * to its seat only when ticked, so it stayed attached to the seat but unseen: a train's drekki driver was missing for
     * players who watched the train arrive and for players riding it out of the Nether (Shane, 2026-11-27; traintest rig:
     * the watcher's game listed the drekki on the carriage, did not draw it, and drew the chicken beside it).
     */
    public static Packet<ClientGamePacketListener> rider(Entity e, Packet<ClientGamePacketListener> packet) {
        if (!(packet instanceof ClientboundAddEntityPacket add) || e.getVehicle() == null) return packet;
        String v = e.getRootVehicle().getClass().getName();
        if (!v.equals(CARRIAGE) && !v.equals(CONTROLLED) && !isContraption(e.getRootVehicle())) return packet;
        if (Math.abs(add.getX() - e.getX()) < 1 && Math.abs(add.getY() - e.getY()) < 1 && Math.abs(add.getZ() - e.getZ()) < 1) return packet;
        LOG.info("{} on a {} spawned on a client {} blocks from its seat: sent at its seat", e.getName().getString(), e.getRootVehicle().getType().toShortString(),
            Math.round(Math.sqrt(e.distanceToSqr(add.getX(), add.getY(), add.getZ()))));
        return new ClientboundAddEntityPacket(e.getId(), e.getUUID(), e.getX(), e.getY(), e.getZ(), add.getXRot(), add.getYRot(), e.getType(), add.getData(),
            e.getDeltaMovement(), add.getYHeadRot());
    }

    private static boolean isContraption(Entity v) {
        for (Class<?> c = v.getClass(); c != null; c = c.getSuperclass())
            if (c.getName().equals("com.simibubi.create.content.contraptions.AbstractContraptionEntity")) return true;
        return false;
    }

    /**
     * Client. A train carriage new in this game (its first second) takes the place in its synced data at once.
     * <p>
     * Create's client moves a carriage from where it last had its wheels on the track graph toward the synced place, along
     * the track. A carriage that left the overworld at one portal and came back at another (L&S into the Nether, out at
     * the coal drill) still had its wheels at the first portal in this game, with no way along the track between them in
     * the overworld: the new carriage entity stayed at the L&S portal, 454 blocks away, with its riders. Players at the
     * coal drill saw no train, or a train without its drekki driver; a player riding it saw no driver (Shane,
     * 2026-11-27; traintest rig runs D to E2: the watcher's game held the carriage at -834 72 1651, the server at the coal
     * drill).
     */
    public static void snap(Entity entity, com.simibubi.create.content.trains.entity.Carriage carriage) {
        if (!entity.level().isClientSide || entity.tickCount > 20 || carriage == null) return;
        var dce = carriage.getDimensional(entity.level());
        if (dce == null || !dce.pointsInitialised) return;
        dce.pointsInitialised = false;
        LOG.once("snap", "train carriages new in this game take their synced place at once (not slid from an old portal)");
    }
}
