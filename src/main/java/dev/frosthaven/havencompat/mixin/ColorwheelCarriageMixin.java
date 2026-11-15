package dev.frosthaven.havencompat.mixin;

import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.frosthaven.havencompat.colorwheel.ColorwheelCut;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Immersive Portals' record of which embedding draws which train carriage: Colorwheel's embeddings join it; see {@link ColorwheelCut}. */
@Pseudo
@Mixin(targets = ColorwheelCut.CARRIAGES, remap = false)
public abstract class ColorwheelCarriageMixin {
    @Inject(method = "registerEmbedding", at = @At("HEAD"))
    private static void haven_compat$colorwheelCarriage(VisualEmbedding embedding, Entity entity, CallbackInfo ci) {
        ColorwheelCut.registerCarriage(embedding, entity);
    }
}
