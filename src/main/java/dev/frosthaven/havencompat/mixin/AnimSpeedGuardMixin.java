package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.animations.AnimSpeedGuard;
import dev.frosthaven.havencompat.animations.SpeedStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** playerAnimator's speed step always ends; see {@link SpeedStep}. require = 0: another build keeps its own. */
@Pseudo
@Mixin(targets = "dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier", remap = false)
public abstract class AnimSpeedGuardMixin {
    @Shadow public float speed;
    @Shadow private float shiftedDelta;

    @ModifyVariable(method = "step(F)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private float haven_compat$finiteStep(float delta) {
        if (SpeedStep.safe(delta, speed, shiftedDelta)) return delta;
        AnimSpeedGuard.acted(delta, speed, shiftedDelta);
        speed = SpeedStep.speed(speed);
        shiftedDelta = SpeedStep.shifted(shiftedDelta);
        return SpeedStep.delta(delta, speed, shiftedDelta);
    }
}
