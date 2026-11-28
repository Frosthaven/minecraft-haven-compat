package dev.frosthaven.havencompat.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.frosthaven.havencompat.contraptions.StorageMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A menu a click on a contraption opens belongs to that contraption's entity; see {@link StorageMenus}. */
@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class ContraptionMenuMixin {
    private static final ThreadLocal<AbstractContainerMenu> HAVEN_BEFORE = new ThreadLocal<>();

    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"))
    private void havenCompat$before(Player player, BlockPos localPos, Direction side, InteractionHand hand, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer) HAVEN_BEFORE.set(player.containerMenu);
    }

    @Inject(method = "handlePlayerInteraction", at = @At("RETURN"))
    private void havenCompat$after(Player player, BlockPos localPos, Direction side, InteractionHand hand, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer sp) StorageMenus.clicked(sp, HAVEN_BEFORE.get(), (AbstractContraptionEntity) (Object) this);
        HAVEN_BEFORE.remove();
    }
}
