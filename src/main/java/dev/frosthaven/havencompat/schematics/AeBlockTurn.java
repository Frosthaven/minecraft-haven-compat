package dev.frosthaven.havencompat.schematics;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.RelativeSide;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.UnaryOperator;

/**
 * AE2 blocks keep their orientation in block state properties (facing, spin; a pattern provider's push direction) but
 * do not implement {@code rotate} or {@code mirror}, so a rotated or mirrored structure (a Create schematic, a vanilla
 * structure block) placed drives, pattern providers, inscribers and the rest facing the way they were saved. This
 * turns those properties ({@code AeBlockTurnMixin} adds the two methods to {@code AEBaseBlock}).
 */
public final class AeBlockTurn {
    private AeBlockTurn() {
    }

    /** {@code state} with its front, top and push direction turned by {@code turn}. */
    public static BlockState turned(BlockState state, UnaryOperator<Direction> turn) {
        BlockState out = state;
        IOrientationStrategy strategy = IOrientationStrategy.get(state);
        if (!strategy.getProperties().isEmpty()) {
            Direction front = strategy.getFacing(state);
            Direction top = strategy.getSide(state, RelativeSide.TOP);
            out = strategy.setOrientation(state, turn.apply(front), turn.apply(top));
        }
        if (out.hasProperty(PatternProviderBlock.PUSH_DIRECTION)) {
            Direction push = out.getValue(PatternProviderBlock.PUSH_DIRECTION).getDirection();
            if (push != null) {
                out = out.setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.fromDirection(turn.apply(push)));
            }
        }
        return out;
    }
}
