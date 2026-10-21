package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import dev.frosthaven.havencompat.portals.PortalPipeModel;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Immersive Portals' pipe-model mixin, again, on the methods TFMG merged into Create's pipe model; see
 * {@link PortalPipeModel}. Priority 1500 so it applies after TFMG's mixin (1000) and may inject into its methods.
 */
@Mixin(value = PipeAttachmentModel.class, remap = false, priority = 1500)
public abstract class PortalPipesTfmgMixin {
    @WrapOperation(method = "gatherModelData", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/fluids/FluidTransportBehaviour;getRenderedRimAttachment(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Lcom/simibubi/create/content/fluids/FluidTransportBehaviour$AttachmentTypes;"))
    private AttachmentTypes haven_compat$portalAttachment(FluidTransportBehaviour transport, BlockAndTintGetter world, BlockPos pos,
                                                          BlockState state, Direction side, Operation<AttachmentTypes> original) {
        return PortalPipeModel.attachment(original.call(transport, world, pos, state, side), transport, world, pos, state, side);
    }

    @Inject(method = "gatherModelData", at = @At("RETURN"))
    private void haven_compat$portalFaces(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state,
                                          ModelData blockEntityData, CallbackInfoReturnable<ModelData.Builder> cir) {
        PortalPipeModel.addPortalFaces(cir.getReturnValue(), world, pos, state);
    }

    @Inject(method = "getQuads", at = @At("RETURN"), cancellable = true)
    private void haven_compat$portalFillers(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType,
                                            CallbackInfoReturnable<List<BakedQuad>> cir) {
        List<BakedQuad> quads = PortalPipeModel.withFillers(cir.getReturnValue(), state, side, rand, data, renderType);
        if (quads != null) {
            cir.setReturnValue(quads);
        }
    }
}
