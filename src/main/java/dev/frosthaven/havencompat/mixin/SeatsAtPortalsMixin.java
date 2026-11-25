package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.frosthaven.havencompat.portals.SeatsAtPortals;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Train seats past a portal plane keep their rider; see {@link SeatsAtPortals}. */
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.seat.SeatMovementBehaviour", remap = false)
public abstract class SeatsAtPortalsMixin {
    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true)
    private void haven$keepAtPortal(MovementContext context, BlockPos pos, CallbackInfo ci) {
        if (SeatsAtPortals.keep(context, pos)) ci.cancel();
    }
}
