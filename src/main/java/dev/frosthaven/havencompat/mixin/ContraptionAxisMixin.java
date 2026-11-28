package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.frosthaven.havencompat.Log;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create 6.0.10's contraption collision (ContinuousOBBCollider.collideMany) reads its separation axis after a test that
 * can leave it unset (two boxes whose centres coincide): a NullPointerException in the server tick that stops the
 * server ("Cannot read field x because mf.axis is null"; the traintest rig, 2026-11-28, a player seated on the courier
 * train; known upstream, Create issues 10278 and 10344). An unset axis reads as zero: no push that tick, the next tick
 * has a real one. Logs "[contraption collision]" once.
 */
@Mixin(targets = "com.simibubi.create.foundation.collision.ContinuousOBBCollider", remap = false)
public abstract class ContraptionAxisMixin {
    private static final Log HAVEN_LOG = new Log("[contraption collision] ");

    @ModifyExpressionValue(method = "collideMany", at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETFIELD,
        target = "Lcom/simibubi/create/foundation/collision/ContinuousOBBCollider$ContinuousSeparationManifold;axis:Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 havenCompat$axis(Vec3 axis) {
        if (axis != null) return axis;
        HAVEN_LOG.once("unset", "a contraption collision had no separation axis (Create 6.0.10 would crash): no push that tick");
        return Vec3.ZERO;
    }
}
