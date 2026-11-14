package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.spells.CasterItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A right-click on a mob with an Ars spell caster casts. Vanilla asks the mob first and uses the item only when the
 * mob does not consume the click; Ars familiars, villagers and pets consume every click, so within reach no cast was
 * sent. Not sneaking, and the clicked hand holds a caster ({@link CasterItems}): the mob is not asked, the game goes on
 * to {@code useItem}. The off hand's turn is skipped too when the main hand holds a caster: Ars' {@code use} returns
 * PASS, so vanilla goes on to the off hand, and a villager opens its trades for either hand. Sneaking keeps the mob's
 * own interaction. Other entities (boats, item frames) are never skipped.
 */
@Mixin(Minecraft.class)
public abstract class ArsCastOverMobMixin {
    @Unique private static final Log havencompat$LOG = new Log("[spell cast] ");

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;interactAt(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult havencompat$castNotInteractAt(MultiPlayerGameMode gameMode, Player player, Entity target, EntityHitResult hit,
            InteractionHand hand, Operation<InteractionResult> original) {
        return havencompat$casts(player, target, hand) ? InteractionResult.PASS : original.call(gameMode, player, target, hit, hand);
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult havencompat$castNotInteract(MultiPlayerGameMode gameMode, Player player, Entity target, InteractionHand hand,
            Operation<InteractionResult> original) {
        return havencompat$casts(player, target, hand) ? InteractionResult.PASS : original.call(gameMode, player, target, hand);
    }

    @Unique
    private static boolean havencompat$casts(Player player, Entity target, InteractionHand hand) {
        if (!(target instanceof Mob) || player.isSecondaryUseActive() || !(havencompat$caster(player, hand)
                || hand == InteractionHand.OFF_HAND && havencompat$caster(player, InteractionHand.MAIN_HAND))) return false;
        havencompat$LOG.once("used", "a spell caster right-clicked on a mob casts; sneak for the mob's own interaction");
        return true;
    }

    @Unique
    private static boolean havencompat$caster(Player player, InteractionHand hand) {
        return CasterItems.castsOnUse(player.getItemInHand(hand).getItem().getClass());
    }
}
