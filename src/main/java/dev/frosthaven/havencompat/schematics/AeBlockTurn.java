package dev.frosthaven.havencompat.schematics;

import appeng.api.orientation.BlockOrientation;
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
            out = strategy.setOrientation(state, turn.apply(front), spin(turn.apply(front), turn.apply(top)));
        }
        if (out.hasProperty(PatternProviderBlock.PUSH_DIRECTION)) {
            Direction push = out.getValue(PatternProviderBlock.PUSH_DIRECTION).getDirection();
            if (push != null) {
                out = out.setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.fromDirection(turn.apply(push)));
            }
        }
        return out;
    }

    /**
     * The spin whose orientation has {@code top} on top. AE2's {@code BlockOrientation.get(front, up)} does not always
     * return such an orientation (a drive printed with a quarter turn came out upside down), so each spin is tried.
     */
    public static int spin(Direction front, Direction top) {
        for (int spin = 0; spin < 4; spin++) {
            if (BlockOrientation.get(front, spin).getSide(RelativeSide.TOP) == top) {
                return spin;
            }
        }
        return 0;   // top along the front's axis: no spin fits
    }
}
