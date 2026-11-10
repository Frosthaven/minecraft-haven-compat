package dev.frosthaven.havencompat.dyeing;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Create Connected fan dyeing catalyst with a Brass Dye Nozzle: a fan through it dyes with the dye in
 * {@link #POSITION} of the crafting grid ({@link PositionedColoringType}). Right-click with an empty hand: next
 * position; sneak + empty hand: the nozzle comes off and the plain catalyst stays.
 */
public final class ControlledCatalystBlock extends Block implements IWrenchable {
    public static final EnumProperty<DyePosition> POSITION = EnumProperty.create("position", DyePosition.class);

    private final DyeColor color;

    public ControlledCatalystBlock(DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
        registerDefaultState(stateDefinition.any().setValue(POSITION, DyePosition.LEFT));
    }

    public DyeColor color() { return color; }

    /** Create Connected's catalyst of this colour, the block without the nozzle. */
    public static Block plain(DyeColor color) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create_connected", color.getName() + "_fan_dyeing_catalyst"));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POSITION);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                Block plain = plain(color);
                if (plain == Blocks.AIR) {
                    return InteractionResult.PASS;
                }
                level.setBlockAndUpdate(pos, plain.defaultBlockState());
                player.getInventory().placeItemBackInInventory(new ItemStack(Dyeing.NOZZLE.get()));
                AllSoundEvents.WRENCH_REMOVE.playOnServer(level, pos, 1, 1);
            } else {
                level.setBlockAndUpdate(pos, state.setValue(POSITION, state.getValue(POSITION).next()));
                AllSoundEvents.SCROLL_VALUE.playOnServer(level, pos, 1, 1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
