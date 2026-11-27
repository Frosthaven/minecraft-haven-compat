package dev.frosthaven.havencompat.mixin;

import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.frosthaven.havencompat.oxidation.AgedParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Client: a partial model asked for while a visual is built takes its aged copy ({@link AgedParts}). */
@Mixin(value = Models.class, remap = false)
public abstract class AgedPartsModelsMixin {
    @ModifyVariable(method = {"partial(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;)Ldev/engine_room/flywheel/api/model/Model;",
            "partial(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Lnet/minecraft/core/Direction;)Ldev/engine_room/flywheel/api/model/Model;",
            "partial(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Ljava/lang/Object;Ljava/util/function/BiConsumer;)Ldev/engine_room/flywheel/api/model/Model;"},
            at = @At("HEAD"), argsOnly = true)
    private static PartialModel haven_compat$aged(PartialModel partial) {
        return AgedParts.pick(partial);
    }
}
