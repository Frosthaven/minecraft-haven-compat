package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import dev.frosthaven.havencompat.trees.HangingLogs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the hanging branch logs of Ars trees to the logs Create's tree cutter found, before it looks for the leaves
 * (so their leaves are found too); see {@link HangingLogs}.
 */
@Mixin(value = TreeCutter.class, remap = false)
public abstract class ArsTreeCutMixin {
    @Inject(method = "findTree", at = @At(value = "INVOKE", target = "Ljava/util/Set;clear()V", ordinal = 0))
    private static void haven$hangingLogs(BlockGetter reader, BlockPos pos, BlockState brokenState,
            CallbackInfoReturnable<TreeCutter.Tree> cir, @Local(ordinal = 0) List<BlockPos> logs) {
        logs.addAll(HangingLogs.under(logs, BlockPos::below, at -> {
            if (at.getY() <= pos.getY()) {
                return false;
            }
            BlockState state = reader.getBlockState(at);
            return TreeCutter.isLog(state) && !TreeCutter.isLog(reader.getBlockState(at.below()))
                    && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().startsWith("ars_");
        }));
    }
}
