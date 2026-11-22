package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PortalEye;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A train rider's camera does not go past a portal's plane before the rider does; see {@link PortalEye}. */
@Mixin(Camera.class)
public abstract class PortalEyeMixin {
    @Shadow private Vec3 position;

    @Shadow protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void haven_compat$holdAtPortal(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        Vec3 place = PortalEye.hold(entity, detached, position);
        if (place != null) setPosition(place);
    }
}
