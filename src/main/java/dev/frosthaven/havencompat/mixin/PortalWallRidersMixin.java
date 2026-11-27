package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.SeatsAtPortals;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Contraption riders crossing a portal set in a wall do not suffocate in it; see {@link SeatsAtPortals#notInWall}. */
@Mixin(Entity.class)
public abstract class PortalWallRidersMixin {
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void haven$notInPortalWall(CallbackInfoReturnable<Boolean> cir) {
        if (SeatsAtPortals.notInWall((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
