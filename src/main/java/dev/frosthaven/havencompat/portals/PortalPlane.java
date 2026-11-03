package dev.frosthaven.havencompat.portals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * The purple plane of a Disabled portal, drawn from the portal entity's own renderer, so a shader pack treats it like any
 * entity. One tile per block; a tile with solid blocks on both sides of the plane is left out, so a portal that is not a
 * rectangle keeps its frame, and a portal flush with a wall still shows.
 */
public final class PortalPlane {
    private static final Log LOG = new Log("[portal view] ");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double SIDE = 0.01;
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("block/nether_portal");

    /** The portal class's shape getters, looked up once per class instead of each frame. */
    private record Shape(Method normal, Method origin, Method axisW, Method axisH, Method width, Method height) {}

    private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
        @Override
        protected Shape computeValue(Class<?> c) {
            try {
                return new Shape(c.getMethod("getNormal"), c.getMethod("getOriginPos"), c.getMethod("getAxisW"),
                        c.getMethod("getAxisH"), c.getMethod("getWidth"), c.getMethod("getHeight"));
            } catch (NoSuchMethodException e) {
                LOG.error("plane", "the portal's shape methods changed: no purple plane", e);
                return null;
            }
        }
    };

    private PortalPlane() {}

    public static void draw(Entity portal, PoseStack pose, MultiBufferSource buffers) {
        Shape shape = SHAPES.get(portal.getClass());
        if (shape == null) return;
        try {
            Vec3 normal = (Vec3) shape.normal().invoke(portal);
            Vec3 origin = (Vec3) shape.origin().invoke(portal);
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (camera.subtract(origin).dot(normal) <= 0) return;   // the portal's other entity draws that side
            Vec3 axisW = (Vec3) shape.axisW().invoke(portal);
            Vec3 axisH = (Vec3) shape.axisH().invoke(portal);
            double width = (double) shape.width().invoke(portal);
            double height = (double) shape.height().invoke(portal);
            Vec3 side = normal.scale(SIDE);
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(TEXTURE);
            VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            Vec3 toOrigin = origin.subtract(portal.position());
            for (int w = 0; w < Math.ceil(width); w++) {
                for (int h = 0; h < Math.ceil(height); h++) {
                    double w0 = w - width / 2, h0 = h - height / 2;
                    double w1 = Math.min(w0 + 1, width / 2), h1 = Math.min(h0 + 1, height / 2);
                    Vec3 mid = origin.add(axisW.scale((w0 + w1) / 2)).add(axisH.scale((h0 + h1) / 2));
                    if (solid(portal, mid.add(side)) && solid(portal, mid.subtract(side))) continue;
                    corner(out, pose, toOrigin, axisW, axisH, normal, w0, h0, sprite.getU0(), sprite.getV1());
                    corner(out, pose, toOrigin, axisW, axisH, normal, w1, h0, sprite.getU1(), sprite.getV1());
                    corner(out, pose, toOrigin, axisW, axisH, normal, w1, h1, sprite.getU1(), sprite.getV0());
                    corner(out, pose, toOrigin, axisW, axisH, normal, w0, h1, sprite.getU0(), sprite.getV0());
                }
            }
        } catch (ReflectiveOperationException e) {
            LOG.error("plane", "the portal's shape methods changed: no purple plane", e);
        }
    }

    private static boolean solid(Entity portal, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        return portal.level().getBlockState(pos).isSolidRender(portal.level(), pos);
    }

    private static void corner(VertexConsumer out, PoseStack pose, Vec3 toOrigin, Vec3 axisW, Vec3 axisH, Vec3 normal, double w, double h, float u, float v) {
        Vec3 at = toOrigin.add(axisW.scale(w)).add(axisH.scale(h));
        out.addVertex(pose.last().pose(), (float) at.x, (float) at.y, (float) at.z)
                .setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
