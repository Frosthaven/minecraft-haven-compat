package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.MountCrossing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mount correction the server sent before it knew of a portal crossing is not applied; see {@link MountCrossing}. On the
 * packet, as {@link LatePassengersMixin}: on the network thread vanilla's handler only passes the packet to the client
 * thread, so the check runs there.
 */
@Mixin(ClientboundMoveVehiclePacket.class)
public abstract class MountCrossingMixin {
    @Inject(method = "handle(Lnet/minecraft/network/protocol/game/ClientGamePacketListener;)V", at = @At("HEAD"), cancellable = true)
    private void haven_compat$dropStaleCorrection(ClientGamePacketListener listener, CallbackInfo ci) {
        if (listener instanceof ClientPacketListener && Minecraft.getInstance().isSameThread()
                && MountCrossing.stale((ClientboundMoveVehiclePacket) (Object) this)) {
            ci.cancel();
        }
    }
}
