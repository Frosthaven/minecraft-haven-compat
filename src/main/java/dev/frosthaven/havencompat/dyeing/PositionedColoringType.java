package dev.frosthaven.havencompat.dyeing;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.item.ItemHelper;
import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Bulk dyeing through a Controlled Fan Dyeing Catalyst of one colour and position. Create Dragons Plus colours an item
 * without a coloring recipe by a crafting grid with the dye always right of it; here the dye goes where the nozzle
 * says ({@link DyePosition#grid}). Order: Dragons Plus's own coloring recipes (they do not depend on a grid), then the
 * positioned crafting grid, then Dragons Plus's type for the same colour. Particles, the air's colour and dyeing of
 * entities are Dragons Plus's. Results are cached per item and data, as Dragons Plus does, until the recipes reload.
 */
public final class PositionedColoringType implements FanProcessingType {
    private static final Log LOG = new Log("[dyeing] ");
    private static final ResourceLocation SUS_CRAFTING = ResourceLocation.fromNamespaceAndPath("supplementaries", "sus_crafting");
    private static final String COLORING_INPUT = "plus.dragons.createdragonsplus.common.kinetics.fan.coloring.ColoringRecipeInput";
    private static volatile Constructor<?> coloringInput;
    private static volatile boolean coloringInputFailed;

    private final DyeColor color;
    private final DyePosition position;
    private final Map<Key, Optional<ItemStack>> grids = new ConcurrentHashMap<>();
    private final Map<Key, Boolean> recipes = new ConcurrentHashMap<>();
    private volatile RecipeManager cachedFor;
    private FanProcessingType base;

    private record Key(Item item, DataComponentPatch components) {
        static Key of(ItemStack stack) { return new Key(stack.getItem(), stack.getComponentsPatch()); }
    }

    public PositionedColoringType(DyeColor color, DyePosition position) {
        this.color = color;
        this.position = position;
    }

    /** Dragons Plus's coloring type of this colour (particles, air colour, its recipes), or null without it. */
    private FanProcessingType base() {
        if (base == null) {
            base = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "coloring_" + color.getName()));
        }
        return base;
    }

    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ControlledCatalystBlock block && block.color() == color
                && state.getValue(ControlledCatalystBlock.POSITION) == position;
    }

    @Override
    public int getPriority() {
        return 510;   // Dragons Plus's coloring is 500; the catalysts are different blocks, so this only orders the list
    }

    @Override
    public boolean canProcess(ItemStack stack, Level level) {
        FanProcessingType base = base();
        if (hasColoringRecipe(stack, level)) {
            return base != null && base.canProcess(stack, level);
        }
        return positioned(stack, level).isPresent() || (base != null && base.canProcess(stack, level));
    }

    @Override
    public List<ItemStack> process(ItemStack stack, Level level) {
        FanProcessingType base = base();
        if (!hasColoringRecipe(stack, level)) {
            Optional<ItemStack> result = positioned(stack, level);
            if (result.isPresent()) {
                return ItemHelper.multipliedOutput(stack, result.get());
            }
        }
        return base == null ? null : base.process(stack, level);
    }

    @Override
    public void spawnProcessingParticles(Level level, Vec3 pos) {
        FanProcessingType base = base();
        if (base != null) base.spawnProcessingParticles(level, pos);
    }

    @Override
    public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
        FanProcessingType base = base();
        if (base != null) base.morphAirFlow(particleAccess, random);
    }

    @Override
    public void affectEntity(Entity entity, Level level) {
        FanProcessingType base = base();
        if (base != null) base.affectEntity(entity, level);
    }

    private void checkReload(Level level) {
        RecipeManager manager = level.getRecipeManager();
        if (cachedFor != manager) {
            grids.clear();
            recipes.clear();
            cachedFor = manager;
        }
    }

    /** One item crafted with the dye in this position: the first crafting recipe that matches and gives a new item. */
    private Optional<ItemStack> positioned(ItemStack stack, Level level) {
        checkReload(level);
        return grids.computeIfAbsent(Key.of(stack), key -> {
            ItemStack item = stack.copyWithCount(1);
            ItemStack dye = new ItemStack(DyeItem.byColor(color));
            CraftingInput input = position.grid(item, dye);
            for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                if (SUS_CRAFTING.equals(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()))) {
                    continue;
                }
                try {
                    if (!recipe.matches(input, level)) continue;
                    ItemStack result = recipe.assemble(input, level.registryAccess());
                    if (result.getCount() == 1 && !result.is(dye.getItem()) && !ItemStack.isSameItemSameComponents(result, item)) {
                        return Optional.of(result);
                    }
                } catch (RuntimeException e) {
                    LOG.warnOnce("probe " + holder.id(), "crafting recipe {} threw while probing dye position {}: {}", holder.id(), position.getSerializedName(), e.toString());
                }
            }
            return Optional.empty();
        });
    }

    /** Dragons Plus has a coloring recipe of its own for this item and colour (wool and the like): use it as it is. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean hasColoringRecipe(ItemStack stack, Level level) {
        checkReload(level);
        return recipes.computeIfAbsent(Key.of(stack), key -> {
            RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "coloring"));
            Constructor<?> make = coloringInput();
            if (type == null || make == null) return false;
            try {
                RecipeInput input = (RecipeInput) make.newInstance(ResourceLocation.withDefaultNamespace(color.getName()), stack.copyWithCount(1));
                return level.getRecipeManager().getRecipeFor((RecipeType) type, input, level).isPresent();
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.warnOnce("coloring input", "Dragons Plus coloring recipes not readable, the positioned grid colours everything: {}", e.toString());
                return false;
            }
        });
    }

    private static Constructor<?> coloringInput() {
        if (coloringInput == null && !coloringInputFailed) {
            try {
                coloringInput = Class.forName(COLORING_INPUT).getConstructor(ResourceLocation.class, ItemStack.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                coloringInputFailed = true;
                LOG.warnOnce("coloring input class", "no {}: {}", COLORING_INPUT, e.toString());
            }
        }
        return coloringInput;
    }
}
