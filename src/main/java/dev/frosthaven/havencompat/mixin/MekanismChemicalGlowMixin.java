package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.mekanism.ChemicalGlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Client, with Mekanism and Iris: the glow light of a multiblock's rendered contents comes from {@link ChemicalGlow}
 * (Mekanism's own answer, or full bright for a chemical while a shader pack is in use). The receiver is coerced to
 * Object because Mekanism is not on the classpath.
 */
@Mixin(targets = ChemicalGlow.TARGET, remap = false)
public abstract class MekanismChemicalGlowMixin {
    @Redirect(method = "renderObject(Lmekanism/client/render/data/RenderData;Lnet/minecraft/core/BlockPos;"
            + "Lmekanism/client/render/MekanismRenderer$Model3D;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;IF)I",
            at = @At(value = "INVOKE", target = "Lmekanism/client/render/data/RenderData;calculateGlowLight(I)I"))
    private int haven_compat$glow(@Coerce Object data, int light) {
        return ChemicalGlow.glow(data, light);
    }
}
