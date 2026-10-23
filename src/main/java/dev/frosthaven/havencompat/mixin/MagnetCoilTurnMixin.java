package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.schematics.StateTurn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Simple Magnets' demagnetization coils had no {@code rotate}/{@code mirror}: a turned schematic kept their saved facing. */
@Pseudo
@Mixin(targets = "com.supermartijn642.simplemagnets.DemagnetizationCoilBlock", remap = false)
public abstract class MagnetCoilTurnMixin extends Block {
    private MagnetCoilTurnMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return StateTurn.namedDirection(state, "facing", rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return StateTurn.namedDirection(state, "facing", mirror::mirror);
    }
}
