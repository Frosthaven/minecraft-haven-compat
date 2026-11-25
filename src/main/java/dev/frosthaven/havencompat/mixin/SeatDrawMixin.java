package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havencompat.contraptions.FloorDraw;
import dev.frosthaven.havencompat.contraptions.SeatDraw;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Draws a seated rider of a Create train where its seat is drawn ({@link SeatDraw}) and another player standing on a moving
 *  lift on its floor ({@link FloorDraw}). On the dispatcher, so every pass
 *  (world, Iris shadow, Immersive Portals views) gets the same position. */
@Mixin(EntityRenderDispatcher.class)
public abstract class SeatDrawMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void haven_compat$onSeat(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack pose,
                                     MultiBufferSource buffers, int light, Operation<Void> original) {
        Vec3 d = SeatDraw.shift(entity, partialTick);
        if (d == Vec3.ZERO) d = FloorDraw.shift(entity, partialTick);
        original.call(entity, x + d.x, y + d.y, z + d.z, yaw, partialTick, pose, buffers, light);
    }
}
