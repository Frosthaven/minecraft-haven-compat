package dev.frosthaven.havencompat.trains;

import dev.frosthaven.havencompat.Log;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;

/**
 * Server side. A Create train carriage sometimes stayed invisible on a client until it left view and came back
 * (Create issue 10660; at Haven's Village Underground station 5 of 30 arrivals at normal settings, 2026-11-03).
 *
 * <p>The server starts sending an entity once the chunk it is in now is in the player's view
 * ({@code ChunkMap.TrackedEntity.updatePlayer}), but the vanilla spawn packet carries {@code ServerEntity}'s last sent
 * position, which a carriage (update interval 3) moves only every third tick. A carriage that already existed on the
 * server (the player's render distance below the simulation distance, or another player near the train) and just
 * crossed into the player's outermost chunk spawned up to 2 ticks of travel behind, in a chunk the client never
 * received. The client does not tick an entity in a chunk it does not hold, and a carriage moves and becomes
 * renderable only in its own client tick ({@code CarriageContraptionEntity.tickContraption}); it also skips server
 * move and teleport packets for it ({@code isControlledByLocalInstance()} is true). So it stayed frozen there.
 *
 * <p>The spawn packet now carries the carriage's real position, which is in a chunk the player was sent. Clients need
 * nothing: the server alone fixes it for every player.
 */
public final class CarriageSpawn {
    /** Create: the entity of one train carriage. */
    public static final String TARGET = "com.simibubi.create.content.trains.entity.CarriageContraptionEntity";
    private static final Log LOG = new Log("[trains] ");

    private CarriageSpawn() {
    }

    /** The spawn packet with the carriage's position now (vanilla's uses the last position sent). */
    public static Packet<ClientGamePacketListener> packet(Entity carriage) {
        LOG.once("on", "train carriages spawn on clients at their real position (the last position sent can lie in a chunk the client never got)");
        return new ClientboundAddEntityPacket(carriage.getId(), carriage.getUUID(), carriage.getX(), carriage.getY(), carriage.getZ(),
                carriage.getXRot(), carriage.getYRot(), carriage.getType(), 0, carriage.getDeltaMovement(), carriage.getYHeadRot());
    }
}
