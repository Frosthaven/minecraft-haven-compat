package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageSyncData;
import dev.frosthaven.havencompat.contraptions.ContraptionSpawn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A carriage new in a player's game takes its synced place at once; see {@link ContraptionSpawn#snap}. */
@Mixin(value = CarriageSyncData.class, remap = false)
public abstract class CarriageSnapMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void havenCompat$snap(CarriageContraptionEntity entity, Carriage carriage, CallbackInfo ci) {
        ContraptionSpawn.snap(entity, carriage);
    }
}
