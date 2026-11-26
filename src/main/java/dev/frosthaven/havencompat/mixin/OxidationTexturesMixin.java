package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.oxidation.MoreOxidation;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client: Create: Oxidation's list of copper textures it swaps for their aged versions, plus {@link MoreOxidation}'s
 *  (aged versions shipped by this mod at the path Create: Oxidation looks up). */
@Mixin(targets = "dev.mrmulle.createoxidized.client.QuadRemapper", remap = false)
public abstract class OxidationTexturesMixin {
    @Inject(method = "loadTextureManifest()Ljava/util/Set;", at = @At("RETURN"), cancellable = true)
    private static void haven_compat$more(CallbackInfoReturnable<Set<String>> cir) {
        Set<String> all = new HashSet<>(cir.getReturnValue());
        all.addAll(MoreOxidation.TEXTURES);
        cir.setReturnValue(Set.copyOf(all));
    }
}
