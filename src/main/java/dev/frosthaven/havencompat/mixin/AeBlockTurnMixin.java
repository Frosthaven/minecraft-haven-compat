package dev.frosthaven.havencompat.mixin;

import appeng.block.AEBaseBlock;
import dev.frosthaven.havencompat.schematics.AeBlockTurn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** {@code rotate} and {@code mirror} for every AE2 block (and ExtendedAE's, on the same base); see {@link AeBlockTurn}. */
@Mixin(value = AEBaseBlock.class, remap = false)
public abstract class AeBlockTurnMixin extends Block {
    private AeBlockTurnMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return AeBlockTurn.turned(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return AeBlockTurn.turned(state, mirror::mirror);
    }
}
