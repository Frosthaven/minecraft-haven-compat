package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.LatePassengers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A rider list the client cannot use yet (no vehicle, or its own player not in that level yet) is kept; see
 * {@link LatePassengers}. The hook is on the packet, not on the client's handler: NukaTeam's Gun Lib 3.2.0 replaces that
 * whole handler from its head and cancels the original, so a hook in the handler does not run.
 */
@Mixin(ClientboundSetPassengersPacket.class)
public abstract class LatePassengersMixin {
    @Inject(method = "handle(Lnet/minecraft/network/protocol/game/ClientGamePacketListener;)V", at = @At("HEAD"))
    private void haven_compat$keepEarlyRiders(ClientGamePacketListener listener, CallbackInfo ci) {
        if (listener instanceof ClientPacketListener client && Minecraft.getInstance().isSameThread()) {
            LatePassengers.seen(client.getLevel(), (ClientboundSetPassengersPacket) (Object) this);
        }
    }
}
