package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.frosthaven.havencompat.contraptions.CarriageCopies;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A carriage's copy in another level is built from the live carriage, not an old snapshot; see {@link CarriageCopies}. */
@Mixin(value = Carriage.class, remap = false)
public abstract class CarriageCopyMixin {
    @Shadow
    private void serialize(Entity e) {}

    @Inject(method = "manageEntities", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/trains/entity/Carriage$DimensionalCarriageEntity;createEntity(Lnet/minecraft/world/level/Level;Z)V"))
    private void havenCompat$fresh(Level level, CallbackInfo ci) {
        CarriageContraptionEntity live = ((Carriage) (Object) this).anyAvailableEntity();
        if (live != null && !live.isRemoved()) {
            serialize(live);
            CarriageCopies.saved(live, level);
        }
    }
}
