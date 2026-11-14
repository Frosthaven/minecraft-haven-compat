package dev.frosthaven.havencompat.copperchest;

import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Haven Backports' Copper Chest as a look of Sophisticated Storage's chests, in every tier. Sophisticated Storage keeps
 * a chest's look as a {@link WoodType} and reads its textures from {@code storage_texture_definitions/<wood>_chest.json},
 * so each copper stage, waxed or not, is one wood type of ours with Mojang's copper chest textures (same layout as the
 * vanilla chest's). A tier upgrade used on a placed Copper Chest gives the Sophisticated chest of that tier with the
 * chest's stage, wax and items ({@code TierUpgrade.addTierUpgradeDefinition}, Sophisticated Storage's own hook).
 * The chest then ages as Mojang's copper does ({@code ChangeOverTimeBlock}: same chance, same rule on copper nearby),
 * honeycomb waxes it, an axe takes the wax off and then scrapes a stage; both halves of a double chest change together,
 * because Sophisticated Storage joins only chests of one look.
 */
public final class CopperLook {
    private static final Log LOG = new Log("[copper look] ");
    private static final String STORAGE = "net.p3pp3rf1y.sophisticatedstorage.";

    private record Look(WeatherState stage, boolean waxed) {
        /** "haven_exposed_copper", "haven_waxed_copper": also the texture definition's file name and the lang key. */
        String woodName() {
            return "haven_" + (waxed ? "waxed_" : "") + stagePrefix() + "copper";
        }

        /** Haven Backports' block: "exposed_copper_chest", "waxed_copper_chest". */
        ResourceLocation backportsChest() {
            return ResourceLocation.fromNamespaceAndPath("haven_backports", (waxed ? "waxed_" : "") + stagePrefix() + "copper_chest");
        }

        private String stagePrefix() {
            return stage == WeatherState.UNAFFECTED ? "" : stage.getSerializedName() + "_";
        }
    }

    private static final Map<WoodType, Look> LOOKS = new IdentityHashMap<>();
    private static final Map<Look, WoodType> TYPES = new HashMap<>();

    private CopperLook() {}

    public static void register(IEventBus modBus) {
        // registry events run one mod after the other; mod constructors run side by side and WoodType's map is not
        // made for that
        modBus.addListener(RegisterEvent.class, e -> {
            if (e.getRegistryKey() == Registries.BLOCK) {
                registerLooks();
            }
        });
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(CopperLook::addTierUpgrades));
    }

    private static void registerLooks() {
        if (!LOOKS.isEmpty()) {
            return;
        }
        for (boolean waxed : new boolean[] {false, true}) {
            for (WeatherState stage : WeatherState.values()) {
                Look look = new Look(stage, waxed);
                WoodType type = WoodType.register(new WoodType(look.woodName(), BlockSetType.COPPER));
                LOOKS.put(type, look);
                TYPES.put(look, type);
            }
        }
    }

    /** For every tier upgrade that takes a vanilla chest: the same upgrade for each Copper Chest, with its look. */
    private static void addTierUpgrades() {
        try {
            Class<?> tierClass = Class.forName(STORAGE + "item.StorageTierUpgradeItem$TierUpgrade");
            Class<?> definitionClass = Class.forName(STORAGE + "item.StorageTierUpgradeItem$VanillaTierUpgradeDefinition");
            Class<?> baseClass = definitionClass.getSuperclass();
            Field definitions = tierClass.getDeclaredField("blockUpgradeDefinitions");
            definitions.setAccessible(true);
            Method add = tierClass.getMethod("addTierUpgradeDefinition", Block.class, definitionClass);
            Constructor<?> create = definitionClass.getConstructor(Class.class, Predicate.class,
                    Class.forName(STORAGE + "block.StorageBlockBase"), WoodType.class, Property[].class);
            Method entityClass = accessible(baseClass, "blockEntityClass");
            Method blocked = accessible(baseClass, "isUpgradingBlocked");
            Method newBlock = accessible(baseClass, "newBlock");
            Method properties = accessible(baseClass, "getPropertiesToCopy");
            int added = 0;
            for (Object tier : tierClass.getEnumConstants()) {
                Object chest = ((Map<?, ?>) definitions.get(tier)).get(Blocks.CHEST);
                if (!definitionClass.isInstance(chest)) {
                    continue;
                }
                for (Map.Entry<Look, WoodType> look : TYPES.entrySet()) {
                    Block copperChest = BuiltInRegistries.BLOCK.get(look.getKey().backportsChest());
                    if (copperChest == Blocks.AIR) {
                        LOG.warnOnce("missing", "no block {}: its tier upgrades are not added", look.getKey().backportsChest());
                        continue;
                    }
                    Object definition = create.newInstance(entityClass.invoke(chest), blocked.invoke(chest), newBlock.invoke(chest),
                            look.getValue(), ((List<?>) properties.invoke(chest)).toArray(new Property<?>[0]));
                    add.invoke(tier, copperChest, definition);
                    added++;
                }
            }
            LOG.info("tier upgrades work on Copper Chests ({} definitions)", added);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("upgrades", "Sophisticated Storage's tier upgrades changed: Copper Chests cannot be upgraded", e);
        }
    }

    private static Method accessible(Class<?> owner, String name) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    /** Mojang's {@code ChangeOverTimeBlock.changeOverTime} and {@code getNextState}, for a stage kept in the chest. */
    public static void randomTick(ServerLevel level, BlockPos pos, RandomSource random) {
        Look look = lookAt(level, pos);
        if (look == null || look.waxed() || look.stage() == WeatherState.OXIDIZED || random.nextFloat() >= 0.05688889F) {
            return;
        }
        int age = look.stage().ordinal();
        int same = 0;
        int older = 0;
        for (BlockPos near : BlockPos.withinManhattan(pos, 4, 4, 4)) {
            if (near.distManhattan(pos) > 4) {
                break;
            }
            if (near.equals(pos)) {
                continue;
            }
            WeatherState other = agingStageAt(level, near);
            if (other == null) {
                continue;
            }
            if (other.ordinal() < age) {
                return;
            }
            if (other.ordinal() > age) {
                older++;
            } else {
                same++;
            }
        }
        float share = (float) (older + 1) / (float) (older + same + 1);
        float chance = share * share * (look.stage() == WeatherState.UNAFFECTED ? 0.75F : 1.0F);
        if (random.nextFloat() < chance) {
            setLook(level, pos, new Look(WeatherState.values()[age + 1], false));
        }
    }

    /** The stage of copper that still ages at a position: a vanilla weathering block or an unwaxed chest of ours. */
    private static WeatherState agingStageAt(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof ChangeOverTimeBlock<?> block && block.getAge() instanceof WeatherState stage) {
            return stage;
        }
        Look look = lookAt(level, pos);
        return look == null || look.waxed() ? null : look.stage();
    }

    /**
     * Honeycomb waxes, an axe takes wax off or scrapes one stage back, as on Mojang's copper.
     *
     * @return null when the click is not one of these on a copper look chest (Sophisticated Storage goes on)
     */
    public static ItemInteractionResult use(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
        Look look = lookAt(level, pos);
        if (look == null) {
            return null;
        }
        Look to;
        SoundEvent sound;
        int particles;
        if (stack.is(Items.HONEYCOMB) && !look.waxed()) {
            to = new Look(look.stage(), true);
            sound = SoundEvents.HONEYCOMB_WAX_ON;
            particles = 3003;
        } else if (look.waxed() && stack.canPerformAction(ItemAbilities.AXE_WAX_OFF)) {
            to = new Look(look.stage(), false);
            sound = SoundEvents.AXE_WAX_OFF;
            particles = 3004;
        } else if (!look.waxed() && look.stage() != WeatherState.UNAFFECTED && stack.canPerformAction(ItemAbilities.AXE_SCRAPE)) {
            to = new Look(WeatherState.values()[look.stage().ordinal() - 1], false);
            sound = SoundEvents.AXE_SCRAPE;
            particles = 3005;
        } else {
            return null;
        }
        if (!level.isClientSide) {
            for (BlockPos half : setLook(level, pos, to)) {
                level.levelEvent(null, particles, half, 0);
            }
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (stack.is(Items.HONEYCOMB)) {
                stack.consume(1, player);
            } else {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Look lookAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WoodHolder chest ? LOOKS.get(chest.getWoodType().orElse(null)) : null;
    }

    /** Gives the chest and the other half of a double chest the look; returns the positions changed. */
    private static List<BlockPos> setLook(Level level, BlockPos pos, Look look) {
        List<BlockPos> halves = new ArrayList<>(List.of(pos));
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            halves.add(pos.relative(state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.LEFT
                    ? facing.getClockWise() : facing.getCounterClockWise()));
        }
        halves.removeIf(half -> lookAt(level, half) == null);
        for (BlockPos half : halves) {
            BlockEntity chest = level.getBlockEntity(half);
            ((WoodHolder) chest).setWoodType(TYPES.get(look));
            chest.setChanged();
            BlockState halfState = level.getBlockState(half);
            level.sendBlockUpdated(half, halfState, halfState, Block.UPDATE_ALL);
        }
        return halves;
    }
}
