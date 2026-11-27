package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.contraptions.actors.seat.ContraptionPlayerPassengerRotation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** {@link dev.frosthaven.havencompat.portals.SeatTurn}: Create's per-frame turn of a seated player, minus impossible steps */
@Mixin(value = ContraptionPlayerPassengerRotation.class, remap = false)
public abstract class SeatTurnMixin {
    @Redirect(method = "frame", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setYRot(F)V"))
    private static void havencompat$turn(Player p, float yaw) { dev.frosthaven.havencompat.portals.SeatTurn.turn(p, yaw); }
}
