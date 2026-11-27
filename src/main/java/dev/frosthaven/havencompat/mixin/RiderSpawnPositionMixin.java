package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.contraptions.ContraptionSpawn;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A rider of a Create contraption spawns on clients at its real position; see {@link ContraptionSpawn#rider}. */
@Mixin(ServerEntity.class)
public abstract class RiderSpawnPositionMixin {
    @Shadow @Final private Entity entity;

    @Redirect(method = "sendPairingData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getAddEntityPacket(Lnet/minecraft/server/level/ServerEntity;)Lnet/minecraft/network/protocol/Packet;"))
    private Packet<ClientGamePacketListener> havenCompat$riderSpawn(Entity e, ServerEntity self) {
        return ContraptionSpawn.rider(e, e.getAddEntityPacket(self));
    }
}
