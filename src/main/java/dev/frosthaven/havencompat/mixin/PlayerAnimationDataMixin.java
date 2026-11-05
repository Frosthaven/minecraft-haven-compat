package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.animations.PlayerAnimationData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player Animation Data Optimized: ETF's nbt rules read a small copy of a player's data; see {@link PlayerAnimationData}. */
@Mixin(targets = "traben.entity_texture_features.features.property_reading.properties.optifine_properties.NBTProperty", remap = false)
public abstract class PlayerAnimationDataMixin {
    @Inject(method = "getEntityNBT", at = @At("HEAD"), cancellable = true)
    private void haven_compat$smallPlayerCopy(@Coerce Object state, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag copy = PlayerAnimationData.copyFor(state);
        if (copy != null) cir.setReturnValue(copy);
    }
}
