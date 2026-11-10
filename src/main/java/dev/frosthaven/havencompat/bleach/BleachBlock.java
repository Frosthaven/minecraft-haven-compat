package dev.frosthaven.havencompat.bleach;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/** The bleach fluid's block: anything living inside it gets {@link BleachExposure}. */
public final class BleachBlock extends LiquidBlock {
    public BleachBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        BleachExposure.expose(entity);
    }
}
