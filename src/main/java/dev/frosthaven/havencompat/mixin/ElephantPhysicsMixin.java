package dev.frosthaven.havencompat.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Living Things 2.3.2's elephant answers isControlledByLocalInstance() with "a player rides me" only. Vanilla
 * LivingEntity.travel() moves a mob only when that answer is true, so on the server an elephant without a rider gets
 * no gravity and no walking: it floats and stands still. Without a player rider, this gives the vanilla answer
 * (the server moves the mob). Riding stays as Living Things has it.
 */
@Pseudo
@Mixin(targets = "com.tristankechlo.livingthings.entity.ElephantEntity", remap = false)
public abstract class ElephantPhysicsMixin {
    @Inject(method = "isControlledByLocalInstance", at = @At("HEAD"), cancellable = true)
    private void havenCompat$serverMovesUnriddenElephant(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.getControllingPassenger() instanceof Player)) cir.setReturnValue(!self.level().isClientSide);
    }
}
