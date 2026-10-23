package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.util.StringRepresentable;

import java.util.function.UnaryOperator;

/**
 * {@code rotate}/{@code mirror} for blocks that lack them (the mixins in {@code mixin/*TurnMixin}), by property name, so
 * no mod class is linked: six side booleans ({@code north} ...), or an enum property whose values name a direction
 * (Simple Magnets' {@code facing}; Refined Storage's {@code direction}, also {@code up_north}-style: the horizontal part turns).
 */
public final class StateTurn {
    private StateTurn() {
    }

    /** Each side boolean takes the value of the side that turns onto it. */
    public static BlockState sides(BlockState state, UnaryOperator<Direction> turn) {
        BlockState out = state;
        for (Direction side : Direction.values()) {
            if (property(state, side.getSerializedName()) instanceof BooleanProperty from
                    && property(state, turn.apply(side).getSerializedName()) instanceof BooleanProperty to) {
                out = out.setValue(to, state.getValue(from));
            }
        }
        return out;
    }

    /** An enum property whose value names hold a direction ({@code north}, {@code up_east}): the direction part turns. */
    public static BlockState namedDirection(BlockState state, String name, UnaryOperator<Direction> turn) {
        return property(state, name) instanceof EnumProperty<?> property ? turnEnum(state, property, turn) : state;
    }

    private static <E extends Enum<E> & StringRepresentable> BlockState turnEnum(BlockState state, EnumProperty<E> property, UnaryOperator<Direction> turn) {
        String now = state.getValue(property).getSerializedName();
        String turned = turnedName(now, turn);
        for (E value : property.getPossibleValues()) {
            if (value.getSerializedName().equalsIgnoreCase(turned)) {
                return state.setValue(property, value);
            }
        }
        return state;
    }

    /** {@code north} -> {@code east}; {@code up_north} -> {@code up_east} (the last part is the horizontal facing). */
    static String turnedName(String name, UnaryOperator<Direction> turn) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        int cut = lower.lastIndexOf('_');
        Direction side = Direction.byName(lower.substring(cut + 1));
        return side == null ? lower : lower.substring(0, cut + 1) + turn.apply(side).getSerializedName();
    }

    private static Property<?> property(BlockState state, String name) {
        return state.getBlock().getStateDefinition().getProperty(name);
    }
}
