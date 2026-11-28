package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.oxidation.AgedParts;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client: the block state of a contraption's block entity whose visual Create builds ({@code ContraptionVisual.setupVisualizer},
 * not Flywheel's world storage), for {@link AgedParts}: an aged copper shaft on a train drew its spinning part new
 * (rig screenshot, 2026-11-28; every contraption since AgedParts came in).
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.render.ContraptionVisual", remap = false)
public abstract class AgedPartsContraptionMixin {
    @Inject(method = "setupVisualizer", at = @At("HEAD"))
    private void haven_compat$building(BlockEntity be, float partialTick, CallbackInfo ci) {
        AgedParts.building(be.getBlockState());
    }

    @Inject(method = "setupVisualizer", at = @At("RETURN"))
    private void haven_compat$built(BlockEntity be, float partialTick, CallbackInfo ci) {
        AgedParts.building(null);
    }
}
