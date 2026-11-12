package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.contraptions.ContraptionGround;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Create's "standing on a contraption" report from a rider; see {@link ContraptionGround}. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.sync.ClientMotionPacket", remap = false)
public abstract class ContraptionMotionMixin {
    @Inject(method = "handle", at = @At("TAIL"))
    private void havencompat$stood(ServerPlayer player, CallbackInfo ci) {
        ContraptionGround.reported(player);
    }
}
