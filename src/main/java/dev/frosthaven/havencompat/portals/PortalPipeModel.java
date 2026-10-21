package dev.frosthaven.havencompat.portals;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import dev.frosthaven.havencompat.Log;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Client side. Immersive Portals: Aeronautics 2.4.15 draws Create fluid pipes that open onto a portal (a connection
 * piece in place of the open rim, filler pieces up to the portal plane) through a mixin on Create's
 * {@link PipeAttachmentModel}. TFMG replaces that model's {@code gatherModelData} ({@code @Overwrite}) and its
 * {@code getQuads}; Mixin refuses to inject into methods another mixin of the same priority merged, so Immersive
 * Portals' whole pipe-model mixin is skipped ("Mixin apply ... failed" in the client log) and those pipes show an open
 * rim with a gap. {@code mixin/PortalPipesTfmgMixin} (priority 1500, after TFMG's) makes the same three calls into
 * Immersive Portals' own {@code IPCreatePortalPipeModel}. It does nothing when Mixin lists Immersive Portals' mixin as
 * applied (a later TFMG or IP release), so the pieces are never drawn twice.
 */
public final class PortalPipeModel {
    private static final String HELPER = "qouteall.imm_ptl.core.compat.create_integration.IPCreatePortalPipeModel";
    private static final Log LOG = new Log("[portal pipes] ");
    private static volatile Handles handles;

    private PortalPipeModel() {
    }

    private record Handles(MethodHandle attachment, MethodHandle faces, MethodHandle fillers) {
    }

    /** Null when Immersive Portals' own pipe-model mixin applied, or its helper is missing (then nothing is changed). */
    private static Handles handles() {
        Handles h = handles;
        if (h == null) {
            h = resolve();
            handles = h;
        }
        return h.attachment() == null ? null : h;
    }

    private static Handles resolve() {
        Handles none = new Handles(null, null, null);
        // Mixin's record of what applied: a failed mixin is left out (its handler methods may still be merged).
        for (IMixinInfo m : Mixins.getMixinsForClass(PipeAttachmentModel.class.getName())) {
            if (m.getClassName().endsWith(".MixinPipeAttachmentModel_PortalPipes")) {
                LOG.once("own", "Immersive Portals' pipe model mixin applied: nothing to do");
                return none;
            }
        }
        try {
            Class<?> c = Class.forName(HELPER, true, PortalPipeModel.class.getClassLoader());
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            Handles h = new Handles(
                    l.findStatic(c, "attachment", MethodType.methodType(AttachmentTypes.class, AttachmentTypes.class,
                            FluidTransportBehaviour.class, BlockAndTintGetter.class, BlockPos.class, BlockState.class, Direction.class)),
                    l.findStatic(c, "addPortalFaces", MethodType.methodType(void.class, ModelData.Builder.class,
                            BlockAndTintGetter.class, BlockPos.class, BlockState.class)),
                    l.findStatic(c, "withFillers", MethodType.methodType(List.class, List.class, BlockState.class,
                            Direction.class, RandomSource.class, ModelData.class, RenderType.class)));
            LOG.once("on", "pipes that open onto a portal get Immersive Portals' connection pieces (its own mixin lost to TFMG's model)");
            return h;
        } catch (Throwable t) {
            LOG.error("resolve", "Immersive Portals' pipe model helper not found; portal pipes keep TFMG's model", t);
            return none;
        }
    }

    public static AttachmentTypes attachment(AttachmentTypes picked, FluidTransportBehaviour transport, BlockAndTintGetter world,
                                             BlockPos pos, BlockState state, Direction side) {
        Handles h = handles();
        if (h == null) {
            return picked;
        }
        try {
            return (AttachmentTypes) h.attachment().invokeExact(picked, transport, world, pos, state, side);
        } catch (Throwable t) {
            LOG.error("attachment", "portal pipe attachment failed", t);
            return picked;
        }
    }

    public static void addPortalFaces(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state) {
        Handles h = handles();
        if (h == null) {
            return;
        }
        try {
            h.faces().invokeExact(builder, world, pos, state);
        } catch (Throwable t) {
            LOG.error("faces", "portal pipe faces failed", t);
        }
    }

    /** The quads with the filler pieces added, or null for no change. */
    @SuppressWarnings("unchecked")
    public static List<BakedQuad> withFillers(List<BakedQuad> quads, BlockState state, Direction side, RandomSource rand,
                                              ModelData data, RenderType renderType) {
        Handles h = handles();
        if (h == null) {
            return null;
        }
        try {
            return (List<BakedQuad>) (List<?>) h.fillers().invokeExact((List) quads, state, side, rand, data, renderType);
        } catch (Throwable t) {
            LOG.error("fillers", "portal pipe fillers failed", t);
            return null;
        }
    }
}
