package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.oxidation.MoreOxidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Create: Oxidation's two checks, widened to {@link MoreOxidation}'s blocks: the stage and waxed properties (asked while
 *  a block is built) and the client's model swap (by block id). */
@Mixin(targets = "dev.mrmulle.createoxidized.OxidationProperties", remap = false)
public abstract class OxidationPropertiesMixin {
    @Inject(method = "classCanCarryProperties(Ljava/lang/Object;)Z", at = @At("RETURN"), cancellable = true)
    private static void haven_compat$ours(Object block, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && block instanceof net.minecraft.world.level.block.Block && MoreOxidation.registering()) cir.setReturnValue(true);
    }

    @Inject(method = "isSupportedModelId(Ljava/lang/String;Ljava/lang/String;)Z", at = @At("RETURN"), cancellable = true)
    private static void haven_compat$ourModels(String namespace, String path, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && MoreOxidation.BLOCKS.contains(namespace + ":" + path)) cir.setReturnValue(true);
    }
}
