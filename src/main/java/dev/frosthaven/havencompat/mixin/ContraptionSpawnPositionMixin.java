package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.contraptions.ContraptionSpawn;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Create's carriage and controlled contraption entities spawn on clients at their real position; see {@link ContraptionSpawn}. */
@Pseudo
@Mixin(targets = {ContraptionSpawn.CARRIAGE, ContraptionSpawn.CONTROLLED}, remap = false)
public abstract class ContraptionSpawnPositionMixin extends Entity {
    private ContraptionSpawnPositionMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return ContraptionSpawn.packet(this);
    }
}
