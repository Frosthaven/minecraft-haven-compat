package dev.frosthaven.havencompat.bleach;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector3f;

/**
 * Bleach's look, as the maintainer chose it ("Light", 2026-11-10): vanilla water's textures tinted pale greenish
 * yellow #ECF1C4 at about 40 % opacity (tint alpha 0x91 x the texture's own 180/255), a pale fog of the same hue,
 * translucent like water.
 */
public final class BleachClient {
    private static final int TINT = 0x91ECF1C4;
    private static final Vector3f FOG = new Vector3f(226 / 255f, 234 / 255f, 180 / 255f);

    private BleachClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(BleachClient::extensions);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) -> {
            if (BleachCatalyst.registered()) event.register((state, level, pos, tint) -> tint == 0 ? TINT & 0xFFFFFF : -1, BleachCatalyst.BLOCK.get());
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) -> {
            if (BleachCatalyst.registered()) event.register((stack, tint) -> tint == 0 ? 0xFF000000 | (TINT & 0xFFFFFF) : -1, BleachCatalyst.BLOCK.get());
        });
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(Bleach.SOURCE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Bleach.FLOWING.get(), RenderType.translucent());
        }));
    }

    private static void extensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
            @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
            @Override public ResourceLocation getOverlayTexture() { return ResourceLocation.withDefaultNamespace("block/water_overlay"); }
            @Override public int getTintColor() { return TINT; }

            @Override
            public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                return new Vector3f(FOG);
            }

            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape) {
                RenderSystem.setShaderFogStart(-8F);
                RenderSystem.setShaderFogEnd(24F);   // clearer than water's fog: bleach is almost clear
            }
        }, Bleach.TYPE.get());
    }
}
