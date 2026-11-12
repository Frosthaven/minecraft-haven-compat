package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.spells.TouchTargetClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Before the use packet: name the entity under the crosshair for an Ars spell caster; see TouchTargets. */
@Mixin(MultiPlayerGameMode.class)
public abstract class ArsTouchHintMixin {
    @Inject(method = "useItem", at = @At("HEAD"))
    private void havencompat$hint(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        TouchTargetClient.beforeUse(player, hand);
    }
}
