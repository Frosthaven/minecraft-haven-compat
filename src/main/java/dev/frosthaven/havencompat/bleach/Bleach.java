package dev.frosthaven.havencompat.bleach;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.frosthaven.havencompat.HavenCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Bleach: a pale, almost clear fluid (Create mixing: water, salt and bone meal, heated; or Mekanism's liquid chlorine
 * with water and salt). It does not make new sources. In it, or in a fan's stream through it: Nausea and slow damage
 * ({@link BleachExposure}). A fan through it bleaches items ({@link BleachingType}, {@link BleachProcess}).
 * Registered only with Create loaded ({@code HavenCompatCommon}); the client look is {@code BleachClient}.
 */
public final class Bleach {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, HavenCompat.ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, HavenCompat.ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HavenCompat.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenCompat.ID);
    private static final DeferredRegister<FanProcessingType> FAN = DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, HavenCompat.ID);

    public static final DeferredHolder<FluidType, FluidType> TYPE = TYPES.register("bleach", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid_type.haven_compat.bleach").canSwim(true).canDrown(true).canExtinguish(true).supportsBoating(true)
            .canConvertToSource(false).fallDistanceModifier(0F).motionScale(0.014D).density(1000).viscosity(1000)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SOURCE = FLUIDS.register("bleach", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING = FLUIDS.register("flowing_bleach", () -> new BaseFlowingFluid.Flowing(properties()));
    public static final DeferredBlock<BleachBlock> BLOCK = BLOCKS.register("bleach", () -> new BleachBlock(SOURCE.get(),
            BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.SAND)));
    public static final DeferredItem<BucketItem> BUCKET = ITEMS.register("bleach_bucket", () -> new BucketItem(SOURCE.get(),
            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredHolder<FanProcessingType, BleachingType> BLEACHING = FAN.register("bleaching", BleachingType::new);
    private static final ResourceKey<CreativeModeTab> CREATE_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("create", "base"));

    private Bleach() {}

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING).block(BLOCK).bucket(BUCKET)
                .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5).explosionResistance(100F);
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FAN.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CREATE_TAB)) event.accept(BUCKET.get());
        });
    }
}
