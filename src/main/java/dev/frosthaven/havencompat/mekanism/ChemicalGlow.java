package dev.frosthaven.havencompat.mekanism;

import dev.frosthaven.havencompat.Log;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Client side. Mekanism draws a multiblock's contents (Dynamic Tank, Industrial Turbine, Boiler, Fission Reactor)
 * through the vanilla translucent block entity render type with the lightmap pinned at full sky light
 * ({@code LightTexture.FULL_SKY}, {@code MekanismTileEntityRenderer.renderObject}). Vanilla's lightmap turns that into
 * full brightness anywhere, so a tank's contents glow even in a dark room; a gaseous chemical also takes its alpha
 * from the fill level (0.2 + the filled fraction, {@code MekanismRenderer.getColorARGB}). An Iris shader pack lights
 * block entities from the scene instead (Complementary: shadow map, sky and block light), so in a roofed room a tank
 * a third full of hydrogen measured (25,30,39) against (4,4,5) walls with Complementary Unbound and Reimagined, where
 * vanilla drew it at 97 grey (2026-10-27): drawn, but not visible. While a shader pack is in use, chemical contents
 * take full block light as well ({@code LightTexture.FULL_BRIGHT}), which every pack renders as lit. Fluids, the
 * fill-level alpha and the no-shaders look are unchanged. Mekanism and Iris are reached by class name only.
 */
public final class ChemicalGlow {
    /** The renderer whose {@code renderObject(RenderData, BlockPos, Model3D, PoseStack, VertexConsumer, int, float)} asks the glow light. */
    public static final String TARGET = "mekanism.client.render.tileentity.MekanismTileEntityRenderer";
    private static final String RENDER_DATA = "mekanism.client.render.data.RenderData";
    private static final String CHEMICAL_DATA = "mekanism.client.render.data.ChemicalRenderData";
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    /** {@code LightTexture.FULL_BRIGHT}: block light 15, sky light 15. */
    static final int FULL_BRIGHT = 0xF000F0;
    private static final Log LOG = new Log("[mekanism] ");
    private static Class<?> chemicalData;
    private static MethodHandle glowLight, shaderPackInUse;
    private static boolean ready, off;

    private ChemicalGlow() {
    }

    /** Mekanism's own answer ({@code data.calculateGlowLight(light)}), or full bright for a chemical under a shader pack. */
    public static int glow(Object data, int light) {
        try {
            if (!ready) {
                init();
            }
            int own = (int) glowLight.invoke(data, light);
            if (off || !chemicalData.isInstance(data) || !(boolean) shaderPackInUse.invoke()) {
                return own;
            }
            LOG.once("glow", "chemical contents of Mekanism multiblocks take full block light while a shader pack is in use");
            return FULL_BRIGHT;
        } catch (Throwable t) {
            if (!off) {
                off = true;
                LOG.error("glow", "could not reach Mekanism's render data or Iris; multiblock contents keep Mekanism's light", t);
            }
            return light;
        }
    }

    private static void init() throws ReflectiveOperationException {
        ClassLoader loader = ChemicalGlow.class.getClassLoader();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> renderData = Class.forName(RENDER_DATA, false, loader);
        glowLight = lookup.unreflect(renderData.getMethod("calculateGlowLight", int.class));
        chemicalData = Class.forName(CHEMICAL_DATA, false, loader);
        Class<?> api = Class.forName(IRIS_API, false, loader);
        Object iris = api.getMethod("getInstance").invoke(null);
        shaderPackInUse = lookup.unreflect(api.getMethod("isShaderPackInUse")).bindTo(iris);
        ready = true;
    }
}
