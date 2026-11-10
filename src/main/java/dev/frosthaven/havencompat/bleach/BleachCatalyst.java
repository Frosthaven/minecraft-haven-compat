package dev.frosthaven.havencompat.bleach;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.frosthaven.havencompat.HavenCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Fan Bleaching Catalyst: Create Connected's empty fan catalyst holding bleach, made like its Fan Splashing
 * Catalyst (a Bleach Bucket used on the empty catalyst, or a Spout with 1000 mB), crafted back to the empty one. A fan
 * through it bleaches like loose bleach ({@link BleachingType#isValidAt}). Registered with Create and Create Connected.
 */
public final class BleachCatalyst {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HavenCompat.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenCompat.ID);
    /** Create Connected's catalyst blocks: iron block base, yellow terracotta map colour, a pickaxe for drops, see-through. */
    public static final DeferredBlock<Block> BLOCK = BLOCKS.register("fan_bleaching_catalyst", () -> new CatalystBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).mapColor(MapColor.TERRACOTTA_YELLOW).requiresCorrectToolForDrops()
                    .noOcclusion().isRedstoneConductor((state, level, pos) -> false)));
    private static final ResourceKey<CreativeModeTab> CONNECTED_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("create_connected", "main"));
    private static volatile boolean registered;

    static {
        ITEMS.registerSimpleBlockItem(BLOCK);
    }

    private static final class CatalystBlock extends Block implements IWrenchable {
        CatalystBlock(Properties properties) { super(properties); }
    }

    private BleachCatalyst() {}

    public static boolean registered() { return registered; }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        registered = true;
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (!event.getTabKey().equals(CONNECTED_TAB)) return;
            Item splashing = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create_connected", "fan_splashing_catalyst"));
            ItemStack ours = new ItemStack(BLOCK.get());
            try {
                if (splashing == Items.AIR) throw new IllegalStateException();
                event.insertAfter(new ItemStack(splashing), ours, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            } catch (RuntimeException e) {
                event.accept(ours);
            }
        });
    }
}
