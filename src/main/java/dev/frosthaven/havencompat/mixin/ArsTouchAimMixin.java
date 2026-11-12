package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havencompat.spells.TouchTargets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Ars Nouveau's server-side spell aim takes the entity the client clicked; see {@link TouchTargets}. */
@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.api.spell.AbstractCaster", remap = false)
public abstract class ArsTouchAimMixin {
    @WrapOperation(method = "castSpell", at = @At(value = "INVOKE",
            target = "Lcom/hollingsworth/arsnouveau/api/util/SpellUtil;rayTrace(Lnet/minecraft/world/entity/Entity;DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult havencompat$clickedTarget(Entity caster, double reach, float partial, boolean fluids, Operation<HitResult> original) {
        HitResult hinted = TouchTargets.hit(caster, reach);
        return hinted != null ? hinted : original.call(caster, reach, partial, fluids);
    }
}
