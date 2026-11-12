package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.contraptions.ContraptionGround;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A contraption rider's own movement update cannot drop them off the ground; see {@link ContraptionGround}. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MovePlayerGroundMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void havencompat$keepGround(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ContraptionGround.afterMove(player);
    }
}
