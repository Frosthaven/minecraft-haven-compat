package dev.frosthaven.havencompat.dyeing;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.frosthaven.havencompat.HavenCompat;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Brass Dye Nozzle and the Controlled Fan Dyeing Catalysts (one per vanilla dye colour, as Create Connected makes
 * its catalysts), with one Create fan processing type per colour and position. Registered only with Create, Create
 * Connected and Create Dragons Plus loaded ({@code HavenCompatCommon}); the assets and data come from
 * {@code tools/dye_nozzle.py}.
 */
public final class Dyeing {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HavenCompat.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenCompat.ID);
    private static final DeferredRegister<FanProcessingType> TYPES = DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, HavenCompat.ID);
    public static final Map<DyeColor, DeferredBlock<ControlledCatalystBlock>> CATALYSTS = new EnumMap<>(DyeColor.class);
    public static final DeferredItem<Item> NOZZLE = ITEMS.register("brass_dye_nozzle", () -> new DyeNozzleItem(new Item.Properties()));
    private static final ResourceKey<CreativeModeTab> CONNECTED_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("create_connected", "main"));

    static {
        for (DyeColor color : DyeColor.values()) {
            // Create Connected's catalyst: iron block base, yellow terracotta map colour, a pickaxe for drops, see-through.
            DeferredBlock<ControlledCatalystBlock> block = BLOCKS.register("controlled_" + color.getName() + "_fan_dyeing_catalyst",
                    () -> new ControlledCatalystBlock(color, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                            .mapColor(MapColor.TERRACOTTA_YELLOW).requiresCorrectToolForDrops().noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)));
            CATALYSTS.put(color, block);
            ITEMS.registerSimpleBlockItem(block);
            for (DyePosition position : DyePosition.values()) {
                TYPES.register("dyeing_" + color.getName() + "_" + position.getSerializedName(), () -> new PositionedColoringType(color, position));
            }
        }
    }

    private Dyeing() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        modBus.addListener(Dyeing::tabs);
    }

    /** Each controlled catalyst right after Create Connected's catalyst of its colour, the nozzle after the last. */
    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CONNECTED_TAB)) {
            return;
        }
        ItemStack last = null;
        for (DyeColor color : DyeColor.values()) {
            Item plain = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create_connected", color.getName() + "_fan_dyeing_catalyst"));
            ItemStack ours = new ItemStack(CATALYSTS.get(color).get());
            if (!add(event, plain == Items.AIR ? null : new ItemStack(plain), ours)) {
                event.accept(ours);
            }
            last = ours;
        }
        if (!add(event, last, new ItemStack(NOZZLE.get()))) {
            event.accept(NOZZLE.get());
        }
    }

    private static boolean add(BuildCreativeModeTabContentsEvent event, ItemStack after, ItemStack stack) {
        if (after == null) return false;
        try {
            event.insertAfter(after, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            return true;
        } catch (RuntimeException e) {
            return false;   // the anchor is not in the tab (Create Connected turned its catalysts off)
        }
    }
}
