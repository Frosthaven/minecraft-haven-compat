package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.trains.CarriageSpawn;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Create's carriage entity spawns on clients at its real position; see {@link CarriageSpawn}. */
@Pseudo
@Mixin(targets = CarriageSpawn.TARGET, remap = false)
public abstract class CarriageSpawnPositionMixin extends Entity {
    private CarriageSpawnPositionMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return CarriageSpawn.packet(this);
    }
}
