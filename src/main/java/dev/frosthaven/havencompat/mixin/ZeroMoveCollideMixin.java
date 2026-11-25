package dev.frosthaven.havencompat.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A zero movement collides with nothing: {@code Entity.collide} returns it at once, before Immersive Portals' portal-aware
 * collision ({@code PortalCollisionHandler.handleOtherSideMove}) checks block shapes on both sides of a nearby portal.
 * <p>
 * A seated player still runs the movement step every tick with a zero vector ({@code LocalPlayer.rideTick} -> {@code aiStep}
 * -> {@code travel} -> {@code move}); next to a train portal set in a wall, Immersive Portals' check of that zero move took
 * over 100 ms on the render thread (traintest rig, 2026-11-24, the stall stack). Collision only shortens a movement, so
 * a zero vector always comes back zero: the result is the same, the work is skipped. Priority 500: before Immersive
 * Portals' own HEAD hook in the same method.
 */
@Mixin(value = Entity.class, priority = 500)
public abstract class ZeroMoveCollideMixin {
    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private void haven$zeroMove(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if (movement.x == 0 && movement.y == 0 && movement.z == 0) cir.setReturnValue(movement);
    }
}
