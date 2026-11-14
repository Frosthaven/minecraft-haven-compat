package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.colorwheel.ColorwheelCut;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Colorwheel embedded environment (a ship, a contraption): what the cut reads, and its plane on each draw; see {@link ColorwheelCut}. */
@Pseudo
@Mixin(targets = ColorwheelCut.ENVIRONMENT, remap = false)
public abstract class ColorwheelEnvironmentMixin implements ColorwheelCut.Env {
    @Shadow @Final private Vec3i renderOrigin;
    @Shadow public int matrixIndex;
    @Shadow private boolean deleted;

    @Inject(method = "setupDraw", at = @At("TAIL"))
    private void haven_compat$shipPlane(CallbackInfo ci) {
        ColorwheelCut.setupDraw(this);
    }

    @Override
    public Vec3i haven_compat$origin() {
        return renderOrigin;
    }

    @Override
    public int haven_compat$index() {
        return matrixIndex;
    }

    @Override
    public boolean haven_compat$deleted() {
        return deleted;
    }
}
