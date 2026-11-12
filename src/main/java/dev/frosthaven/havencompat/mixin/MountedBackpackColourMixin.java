package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.backpacks.MountedBackpackColour;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A backpack on a moving Create contraption drew white; see {@link MountedBackpackColour}. Client ticks fill the render
 * block entity with the backpack stack until that succeeds, and each later render refresh (contents synced) fills it
 * again, since the refresh empties it.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpackscreateintegration.backpack.MountedSophisticatedBackpack", remap = false)
public abstract class MountedBackpackColourMixin {
    @Unique private boolean havencompat$filled;

    @Inject(method = "clientTick", at = @At("HEAD"))
    private void havencompat$fillOnce(Level level, CallbackInfo ci) {
        if (!havencompat$filled) {
            havencompat$filled = MountedBackpackColour.fill(this);
        }
    }

    @Inject(method = "refreshRenderBlockEntity", at = @At("TAIL"))
    private void havencompat$refill(CallbackInfo ci) {
        MountedBackpackColour.fill(this);
    }
}
