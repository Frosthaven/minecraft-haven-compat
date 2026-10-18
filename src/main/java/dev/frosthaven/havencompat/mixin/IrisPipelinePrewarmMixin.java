package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.portals.PipelinePrewarm;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris's {@code PipelineManager.preparePipeline(NamespacedId)}: when it created a new pipeline (the id was not in
 * {@code pipelinesPerDimension} before and is after), {@link PipelinePrewarm#created} builds the other dimensions'
 * pipelines right there. Calls made by the prewarm itself are left alone.
 */
@Pseudo
@Mixin(targets = PipelinePrewarm.TARGET, remap = false)
public abstract class IrisPipelinePrewarmMixin {
    @Shadow @Final private Map<?, ?> pipelinesPerDimension;
    @Unique private Set<Object> haven_compat$before;

    @Inject(method = "preparePipeline", at = @At("HEAD"))
    private void haven_compat$noteIds(CallbackInfoReturnable<?> cir) {
        haven_compat$before = PipelinePrewarm.active() ? null : new HashSet<>(pipelinesPerDimension.keySet());
    }

    @Inject(method = "preparePipeline", at = @At("RETURN"))
    private void haven_compat$afterCreate(CallbackInfoReturnable<?> cir) {
        Set<Object> before = haven_compat$before;
        haven_compat$before = null;
        if (before == null) return;
        for (Object id : pipelinesPerDimension.keySet()) {
            if (!before.contains(id)) {
                PipelinePrewarm.created(this, id, cir.getReturnValue());
                return;
            }
        }
    }
}
