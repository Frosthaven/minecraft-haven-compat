package dev.frosthaven.havencompat.mixin;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.frosthaven.havencompat.oxidation.AgedParts;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client: the block state of the block entity whose Flywheel visual is being built, for {@link AgedParts}. */
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage", remap = false)
public abstract class AgedPartsStorageMixin {
    @Inject(method = "createRaw(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lnet/minecraft/world/level/block/entity/BlockEntity;F)Ldev/engine_room/flywheel/api/visual/BlockEntityVisual;", at = @At("HEAD"))
    private void haven_compat$building(VisualizationContext ctx, BlockEntity be, float partialTick, CallbackInfoReturnable<?> cir) {
        AgedParts.building(be.getBlockState());
    }

    @Inject(method = "createRaw(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lnet/minecraft/world/level/block/entity/BlockEntity;F)Ldev/engine_room/flywheel/api/visual/BlockEntityVisual;", at = @At("RETURN"))
    private void haven_compat$built(VisualizationContext ctx, BlockEntity be, float partialTick, CallbackInfoReturnable<?> cir) {
        AgedParts.building(null);
    }
}
