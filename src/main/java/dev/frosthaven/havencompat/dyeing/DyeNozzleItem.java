package dev.frosthaven.havencompat.dyeing;

import com.simibubi.create.AllSoundEvents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The Brass Dye Nozzle: used on a Create Connected fan dyeing catalyst, it becomes the controlled one (position Left). */
public final class DyeNozzleItem extends Item {
    public DyeNozzleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Block block = level.getBlockState(pos).getBlock();
        for (DyeColor color : DyeColor.values()) {
            if (block != ControlledCatalystBlock.plain(color)) {
                continue;
            }
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, Dyeing.CATALYSTS.get(color).get().defaultBlockState());
                if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
                AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos, 1, 1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
