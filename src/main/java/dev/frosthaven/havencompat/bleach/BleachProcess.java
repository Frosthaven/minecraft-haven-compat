package dev.frosthaven.havencompat.bleach;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.frosthaven.havencompat.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * What bulk bleaching does to one stack (water washing is left as Create and the other mods made it). In order, the
 * first that changes the item:
 *  1. Create's own washing recipe, when it only takes the dye off (red wool -> white wool; not powder -> concrete);
 *  2. the water cauldron's own dye-removing action (vanilla dyed items, banners, shulker boxes, Sophisticated
 *     Backpacks and Storage), run by a fake player against a cauldron below the build height (the cauldron's level
 *     change is refused, the item changes), repeated until it stops changing, so a banner loses every layer;
 *  3. a mod's un-dye recipe (Electro Energetics' electrical panel, Aeroworks' wheel module), assembled as the mod does;
 *  4. the family's undyed base item ({@link DyeFamilies#base}: red candle -> candle), data kept;
 *  5. dye data on a dyeable item with no cauldron action (Ad Astra's jet suit, Aquaculture's bobber): back to the
 *     item's own default colour, or none.
 * Then an item that is still a colour with no base becomes its family's white ({@link DyeFamilies#white}).
 * Server only; on the client {@link #canBleach} answers from the item alone.
 */
public final class BleachProcess {
    private static final Log LOG = new Log("[bleach] ");
    private static final List<ResourceLocation> UNDYE_RECIPES = List.of(
            ResourceLocation.fromNamespaceAndPath("electroenergetics", "crafting/electrical_panel_undye"),
            ResourceLocation.fromNamespaceAndPath("aeroworks", "wheel_module_from_styles"));
    /** Cauldron actions of other mods that remove dye (by class name, the mods are not on the classpath). */
    private static final Set<String> DYE_ACTIONS = Set.of(
            "net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems$BackpackCauldronInteraction",
            "net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks$StorageCauldronInteraction",
            "net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks$WoodStorageCauldronInteraction",
            "net.p3pp3rf1y.sophisticatedstorage.init.ModBlocks$BarrelCauldronInteraction");
    private static final ResourceLocation SPLASHING = ResourceLocation.fromNamespaceAndPath("create", "splashing");
    private static final Map<Key, Optional<ItemStack>> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_STEPS = 16;

    private record Key(Item item, DataComponentPatch components) {}

    private BleachProcess() {}

    public static boolean canBleach(ItemStack stack, Level level) {
        if (level instanceof ServerLevel server) {
            return one(stack, server) != null;
        }
        Item item = stack.getItem();
        return dyeAction(item) != null || DyeFamilies.isDyed(item, level) || stack.has(DataComponents.DYED_COLOR);
    }

    /** The bleached items for a whole stack, or null when bleach would not change it. */
    public static List<ItemStack> process(ItemStack stack, Level level) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()) return null;
        ItemStack one = one(stack, server);
        if (one == null) return null;
        List<ItemStack> out = new ArrayList<>();
        int total = stack.getCount() * one.getCount();
        while (total > 0) {
            int n = Math.min(total, one.getMaxStackSize());
            out.add(one.copyWithCount(n));
            total -= n;
        }
        return out;
    }

    /** One item bleached (count = results per item), or null. Cached per item and data. */
    private static ItemStack one(ItemStack stack, ServerLevel level) {
        Key key = new Key(stack.getItem(), stack.getComponentsPatch());
        Optional<ItemStack> cached = CACHE.get(key);
        if (cached == null) {
            ItemStack r = null;
            try {
                r = uncached(stack.copyWithCount(1), level);
            } catch (RuntimeException e) {
                LOG.error("bleach " + BuiltInRegistries.ITEM.getKey(stack.getItem()), "bleaching " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " failed; the item is left as it was", e);
            }
            cached = Optional.ofNullable(r);
            if (CACHE.size() > 4096) CACHE.clear();
            CACHE.put(key, cached);
        }
        return cached.map(ItemStack::copy).orElse(null);
    }

    private static ItemStack uncached(ItemStack item, ServerLevel level) {
        ItemStack r = createWashing(item, level);
        if (r == null) r = cauldronWash(item, level);
        if (r == null) r = undyeRecipe(item, level);
        if (r == null) {
            Item base = DyeFamilies.base(item.getItem(), level);
            if (base != null) r = item.transmuteCopy(base, 1);
        }
        if (r == null && item.has(DataComponents.DYED_COLOR) && item.is(ItemTags.DYEABLE)) {
            // back to the item's own colour: none for leather, a default for some (Ad Astra's jet suit)
            ItemStack plain = new ItemStack(item.getItem());
            r = item.copy();
            if (plain.has(DataComponents.DYED_COLOR)) r.set(DataComponents.DYED_COLOR, plain.get(DataComponents.DYED_COLOR));
            else r.remove(DataComponents.DYED_COLOR);
        }
        ItemStack current = r == null ? item : r;
        Item white = DyeFamilies.base(current.getItem(), level) == null ? DyeFamilies.white(current.getItem(), level) : null;
        if (white != null) {
            current = current.transmuteCopy(white, current.getCount());
            r = current;
        }
        return r == null || ItemStack.isSameItemSameComponents(r, item) && r.getCount() == item.getCount() ? null : r;
    }

    /** Create's washing recipe for a dyed item, kept only when the result has no colour (or is white). */
    private static ItemStack createWashing(ItemStack item, ServerLevel level) {
        if (!DyeFamilies.isDyed(item.getItem(), level)) return null;
        FanProcessingType splashing = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(SPLASHING);
        if (splashing == null || !splashing.canProcess(item, level)) return null;
        List<ItemStack> out = splashing.process(item, level);
        if (out == null || out.size() != 1) return null;
        String path = BuiltInRegistries.ITEM.getKey(out.get(0).getItem()).getPath();
        var m = DyeFamilies.COLOR.matcher(path);
        return !m.find() || m.group(2).equals("white") ? out.get(0) : null;
    }

    private static CauldronInteraction dyeAction(Item item) {
        CauldronInteraction action = CauldronInteraction.WATER.map().get(item);
        if (action == null) return null;
        if (action == CauldronInteraction.DYED_ITEM || action == CauldronInteraction.BANNER || action == CauldronInteraction.SHULKER_BOX
                || DYE_ACTIONS.contains(action.getClass().getName())) {
            return action;
        }
        return null;
    }

    private static ItemStack cauldronWash(ItemStack item, ServerLevel level) {
        if (dyeAction(item.getItem()) == null) return null;
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos below = new BlockPos(0, level.getMinBuildHeight() - 64, 0);   // setBlock refuses it: no world change
        BlockState water = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
        ItemStack current = item;
        boolean changed = false;
        try {
            for (int step = 0; step < MAX_STEPS; step++) {
                CauldronInteraction action = dyeAction(current.getItem());
                if (action == null) break;
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND, current.copy());
                ItemInteractionResult result = action.interact(water, level, below, player, InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND));
                ItemStack after = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (after.isEmpty()) after = firstItem(player.getInventory().items);
                if (!result.consumesAction() || after.isEmpty() || ItemStack.isSameItemSameComponents(after, current)) break;
                current = after.copyWithCount(1);
                changed = true;
            }
        } finally {
            player.getInventory().clearContent();
        }
        return changed ? current : null;
    }

    private static ItemStack firstItem(NonNullList<ItemStack> items) {
        for (ItemStack s : items) if (!s.isEmpty()) return s;
        return ItemStack.EMPTY;
    }

    private static ItemStack undyeRecipe(ItemStack stack, Level level) {
        if (!DyeFamilies.COLOR.matcher(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).find()) return null;
        for (ResourceLocation id : UNDYE_RECIPES) {
            Optional<Recipe<?>> found = level.getRecipeManager().byKey(id).map(h -> h.value());
            if (found.isEmpty() || !(found.get() instanceof CraftingRecipe recipe)) continue;
            List<ItemStack> cells = new ArrayList<>();
            boolean placed = false;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (!placed && ingredient.test(stack)) {
                    cells.add(stack.copyWithCount(1));
                    placed = true;
                } else {
                    ItemStack[] options = ingredient.getItems();
                    if (options.length == 0) { cells = null; break; }
                    cells.add(options[0].copyWithCount(1));
                }
            }
            if (!placed || cells == null) continue;
            CraftingInput input = CraftingInput.of(cells.size(), 1, cells);
            try {
                if (recipe.matches(input, level)) {
                    ItemStack result = recipe.assemble(input, level.registryAccess());
                    if (!result.isEmpty() && !result.is(stack.getItem())) return result;
                }
            } catch (RuntimeException e) {
                LOG.warnOnce("recipe " + id, "un-dye recipe {} threw: {}", id, e.toString());
            }
        }
        return null;
    }
}
