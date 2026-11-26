package dev.frosthaven.havencompat.waystones;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havencompat.HavenCompat;
import dev.frosthaven.havencompat.oxidation.MoreOxidation;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Three Create waystones (Shane, 2026-11-26): brass, steel (TFMG) and copper, cut from their casings. Each is a Waystones
 * {@link WaystoneBlock} with the mod's own behaviour (its block entity type takes our blocks), crafted like every other
 * waystone: the casing in place of the stone (3), a warp stone, 3 obsidian. The copper one ages with Create: Oxidation
 * (its id is in {@code oxidation/blocks.txt}); {@link CreateWaystoneBlock} keeps both halves at the same stage and wax.
 */
public final class CreateWaystones {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HavenCompat.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenCompat.ID);
    /** Waystones' own block properties (5 hardness, 2000 blast resistance, a pickaxe for drops), with a metal sound */
    private static BlockBehaviour.Properties props(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(5f, 2000f).requiresCorrectToolForDrops();
    }
    public static final DeferredBlock<Block> BRASS = BLOCKS.register("brass_waystone", () -> new CreateWaystoneBlock(props(MapColor.TERRACOTTA_YELLOW, SoundType.METAL)));
    public static final DeferredBlock<Block> STEEL = BLOCKS.register("steel_waystone", () -> new CreateWaystoneBlock(props(MapColor.METAL, SoundType.METAL)));
    /** built inside {@link MoreOxidation#enter}: a DeferredRegister registers through RegisterEvent's helper form, which the
     *  oxidation hooks do not see, so the copper waystone names itself while it is built and gets the aging properties */
    public static final DeferredBlock<Block> COPPER = BLOCKS.register("copper_waystone", () -> {
        MoreOxidation.enter(HavenCompat.ID + ":copper_waystone");
        try { return new CreateWaystoneBlock(props(MapColor.COLOR_ORANGE, SoundType.COPPER)); } finally { MoreOxidation.leave(); }
    });
    private static final ResourceKey<CreativeModeTab> WAYSTONES_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("waystones", "waystones"));

    static {
        ITEMS.registerSimpleBlockItem(BRASS);
        ITEMS.registerSimpleBlockItem(STEEL);
        ITEMS.registerSimpleBlockItem(COPPER);
    }

    private CreateWaystones() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((BlockEntityTypeAddBlocksEvent e) -> e.modify(ModBlockEntities.waystone.get(), BRASS.get(), STEEL.get(), COPPER.get()));
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (!event.getTabKey().equals(WAYSTONES_TAB)) return;
            Item after = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("waystones", "mud_bricks_waystone"));
            ItemStack[] ours = {new ItemStack(BRASS.get()), new ItemStack(STEEL.get()), new ItemStack(COPPER.get())};
            try {
                if (after == Items.AIR) throw new IllegalStateException();
                ItemStack prev = new ItemStack(after);
                for (ItemStack s : ours) { event.insertAfter(prev, s, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); prev = s; }
            } catch (RuntimeException ex) {
                for (ItemStack s : ours) event.accept(s);
            }
        });
    }

    /** A waystone whose two halves share Create: Oxidation's stage and wax: a change to one (aging, an axe, a spray, honeycomb)
     *  reaches the other through the shape update a block change sends its neighbour. */
    public static final class CreateWaystoneBlock extends WaystoneBlock {
        public static final MapCodec<WaystoneBlock> CODEC = simpleCodec(CreateWaystoneBlock::new);

        public CreateWaystoneBlock(Properties properties) { super(properties); }

        @Override
        protected MapCodec<WaystoneBlock> codec() { return CODEC; }

        @Override
        public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
            BlockState out = super.updateShape(state, dir, other, level, pos, otherPos);
            if (!out.is(this) || !other.is(this) || !out.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return out;
            DoubleBlockHalf half = out.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            if (dir != (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN) || other.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == half) return out;
            for (Property<?> p : out.getProperties())
                if (p.getName().startsWith("createoxidized_")) out = copy(out, other, p);
            return out;
        }

        private static <T extends Comparable<T>> BlockState copy(BlockState to, BlockState from, Property<T> p) {
            return from.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
        }
    }
}
