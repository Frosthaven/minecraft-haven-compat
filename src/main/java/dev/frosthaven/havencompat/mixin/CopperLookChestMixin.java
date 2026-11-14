package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.copperchest.CopperLook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sophisticated Storage's chest block (every tier is this class): random ticks for the copper look's aging, and
 * honeycomb and axe clicks before the chest's own item handling ({@link CopperLook}). A chest of another look does
 * nothing on a random tick.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.block.ChestBlock", remap = false)
public abstract class CopperLookChestMixin extends Block {
    private CopperLookChestMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CopperLook.randomTick(level, pos, random);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void haven_compat$copperLook(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir) {
        ItemInteractionResult result = CopperLook.use(stack, level, pos, player, hand);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
