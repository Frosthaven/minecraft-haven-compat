package dev.frosthaven.havencompat.mixin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * A placed Sophisticated Backpack keeps the whole backpack item (custom name too), and its menu title shows that name,
 * but the block entity was not {@link Nameable}: Jade then names the block from its plain item ("Diamond Backpack").
 * Now the block entity gives the stored backpack's name, with its colours and fonts.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity", remap = false)
public abstract class BackpackNameMixin extends BlockEntity implements Nameable {
    @Nullable private static MethodHandle havencompat$wrapper;
    @Nullable private static MethodHandle havencompat$backpack;

    private BackpackNameMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private ItemStack havencompat$stack() {
        try {
            if (havencompat$wrapper == null) {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> wrapperType = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper");
                havencompat$backpack = lookup.findVirtual(wrapperType, "getBackpack", MethodType.methodType(ItemStack.class));
                havencompat$wrapper = lookup.findVirtual(getClass(), "getBackpackWrapper", MethodType.methodType(wrapperType));
            }
            Object wrapper = havencompat$wrapper.invoke((Object) this);
            return wrapper == null ? ItemStack.EMPTY : (ItemStack) havencompat$backpack.invoke(wrapper);
        } catch (Throwable e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public Component getName() {
        ItemStack stack = havencompat$stack();
        return stack.isEmpty() ? getBlockState().getBlock().getName() : stack.getHoverName();
    }

    @Override
    public boolean hasCustomName() {
        return havencompat$stack().has(DataComponents.CUSTOM_NAME);
    }

    @Override
    @Nullable
    public Component getCustomName() {
        return havencompat$stack().get(DataComponents.CUSTOM_NAME);
    }
}
