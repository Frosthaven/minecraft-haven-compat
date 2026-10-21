package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.frosthaven.havencompat.portals.RefusedCrossing;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A refused portal crossing puts the player back in front of the portal, not in its plane; see {@link RefusedCrossing}. */
@Mixin(targets = "qouteall.imm_ptl.core.teleportation.ServerTeleportationManager", remap = false)
public abstract class RefusedCrossingMixin {
    @WrapOperation(method = "onPlayerTeleportedInClient", at = @At(value = "INVOKE",
            target = "Lqouteall/imm_ptl/core/teleportation/ServerTeleportationManager;teleportEntityGeneral(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;"))
    private Entity haven_compat$backInFront(Entity entity, Vec3 pos, ServerLevel level, Operation<Entity> original,
                                            @Local(argsOnly = true) ServerPlayer player,
                                            @Local(argsOnly = true) ResourceKey<Level> dimensionBefore,
                                            @Local(argsOnly = true) Vec3 eyeBefore, @Local String failReason) {
        return original.call(entity, RefusedCrossing.target(player, dimensionBefore, eyeBefore, failReason, pos), level);
    }
}
