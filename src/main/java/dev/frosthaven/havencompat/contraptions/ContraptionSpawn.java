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
}
