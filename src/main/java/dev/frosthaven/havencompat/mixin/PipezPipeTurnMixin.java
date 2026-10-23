package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.schematics.StateTurn;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Pipez pipes keep their six connections in the block state and had no {@code rotate}/{@code mirror}: a turned
 * schematic printed every pipe connected toward its saved sides (the Schematicannon places without shape updates).
 */
@Pseudo
@Mixin(targets = "de.maxhenkel.pipez.blocks.PipeBlock", remap = false)
public abstract class PipezPipeTurnMixin extends Block {
    private PipezPipeTurnMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return StateTurn.sides(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return StateTurn.sides(state, mirror::mirror);
    }
}
