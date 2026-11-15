package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.flywheel.LightBufferFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flywheel attaches a buffer to a buffer texture: the buffer exists first (macOS); see {@link LightBufferFix}. */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.backend.gl.TextureBuffer", remap = false)
public abstract class FlywheelTextureBufferMixin {
    @Inject(method = "bind", at = @At("HEAD"))
    private void haven_compat$existingBuffer(int buffer, CallbackInfo ci) {
        LightBufferFix.ensureExists(buffer);
    }
}
