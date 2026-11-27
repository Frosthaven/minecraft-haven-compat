package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import dev.frosthaven.havencompat.portals.CarriedRiders;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Train passengers left at a portal before the carriage's far copy exists are brought through; see {@link CarriedRiders}. */
@Mixin(targets = "com.simibubi.create.content.trains.entity.Carriage$DimensionalCarriageEntity", remap = false)
public abstract class CarriedRidersMixin {
    @Inject(method = "dismountPlayer", at = @At("RETURN"))
    private void haven$leftBehind(ServerLevel level, ServerPlayer player, Integer seat, boolean capture, CallbackInfo ci) {
        CarriedRiders.dismounted((Carriage.DimensionalCarriageEntity) (Object) this, level, player, seat, capture);
    }

    @Inject(method = "updatePassengerLoadout", at = @At("RETURN"))
    private void haven$handOverMobs(CallbackInfo ci) {
        CarriedRiders.handOver((Carriage.DimensionalCarriageEntity) (Object) this);
    }

    @Inject(method = "updatePassengerLoadout", at = @At("HEAD"))
    private void haven$bringThrough(CallbackInfo ci) {
        CarriedRiders.bring((Carriage.DimensionalCarriageEntity) (Object) this);
    }
}
