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
 * Now the block entity is named from the stored backpack: Jade's title ({@code getDisplayName}) stays the plain
 * backpack name, and SecurityCraft's Jade line ("Custom name: ...", from {@code getCustomName}) shows the custom name
 * with its colours and fonts.
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

    /** The plain backpack name ("Diamond Backpack"): Jade's title. The custom name stays in {@link #getCustomName()}. */
    @Override
    public Component getDisplayName() {
        ItemStack stack = havencompat$stack();
        return stack.isEmpty() ? getBlockState().getBlock().getName() : stack.getItem().getName(stack);
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
