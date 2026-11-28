package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.frosthaven.havencompat.oxidation.ContraptionAging;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copper on a contraption ages; see {@link ContraptionAging}. */
@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class ContraptionAgingMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void havenCompat$age(CallbackInfo ci) {
        ContraptionAging.tick((AbstractContraptionEntity) (Object) this);
    }
}
