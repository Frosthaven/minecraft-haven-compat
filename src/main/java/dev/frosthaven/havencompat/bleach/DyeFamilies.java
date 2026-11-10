package dev.frosthaven.havencompat.bleach;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * The pack's dyed item families. An item with a colour in its id is dyed when a crafting recipe makes it from a dye
 * and something else, or when its family (the id with the colour swapped) exists in at least 12 of the 16 colours
 * (banners, glazed terracotta, Waystones' portstones are not made from dye). Dyes themselves are never dyed items. Its
 * base is the item with the colour (and "stained") taken out of the id, when that exists (red candle -> candle,
 * reinforced red stained glass -> reinforced glass, red portstone -> portstone); its white is the id with the colour
 * replaced by white. Rebuilt when the recipes reload.
 */
public final class DyeFamilies {
    static final Pattern COLOR = Pattern.compile("(^|_)(white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red|black)(_|$)");
    private static final TagKey<Item> DYES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dyes"));
    private static volatile RecipeManager builtFor;
    private static volatile Set<Item> dyed = Set.of();
    private static volatile Map<Item, Item> toBase = Map.of();

    private DyeFamilies() {}

    /** The undyed base of a dyed item, or null. */
    public static Item base(Item item, Level level) {
        build(level);
        return toBase.get(item);
    }

    public static boolean isDyed(Item item, Level level) {
        build(level);
        return dyed.contains(item);
    }

    /** The white member of a dyed item's family, or null (none, or it is white already). */
    public static Item white(Item item, Level level) {
        if (!isDyed(item, level)) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        Matcher m = COLOR.matcher(id.getPath());
        if (!m.find() || m.group(2).equals("white")) return null;
        String path = id.getPath().substring(0, m.start(2)) + "white" + id.getPath().substring(m.end(2));
        Item white = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path));
        return white == Items.AIR || white == item ? null : white;
    }

    private static synchronized void build(Level level) {
        RecipeManager manager = level.getRecipeManager();
        if (builtFor == manager) return;
        Set<Item> found = new HashSet<>();
        Map<Item, Item> bases = new HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            List<Ingredient> ingredients;
            ItemStack result;
            try {
                ingredients = holder.value().getIngredients();
                result = holder.value().getResultItem(level.registryAccess());
            } catch (RuntimeException e) {
                continue;
            }
            if (ingredients.isEmpty() || result.isEmpty()) continue;
            boolean dye = false, other = false;
            for (Ingredient ing : ingredients) {
                if (ing.isEmpty()) continue;
                ItemStack[] items = ing.getItems();
                boolean allDyes = items.length > 0;
                for (ItemStack s : items) allDyes &= s.is(DYES);
                if (allDyes) dye = true; else other = true;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (!dye || !other || !COLOR.matcher(id.getPath()).find()) continue;
            found.add(result.getItem());
        }
        // families present in most colours
        Map<String, Set<String>> colours = new HashMap<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            Matcher m = COLOR.matcher(id.getPath());
            if (!m.find()) continue;
            String family = id.getNamespace() + ":" + id.getPath().substring(0, m.start(2)) + "{}" + id.getPath().substring(m.end(2));
            colours.computeIfAbsent(family, k -> new HashSet<>()).add(m.group(2));
        }
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            Matcher m = COLOR.matcher(id.getPath());
            if (!m.find()) continue;
            String family = id.getNamespace() + ":" + id.getPath().substring(0, m.start(2)) + "{}" + id.getPath().substring(m.end(2));
            if (colours.get(family).size() >= 12) found.add(BuiltInRegistries.ITEM.get(id));
        }
        found.removeIf(item -> new ItemStack(item).is(DYES));
        for (Item item : found) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            for (String path : baseIds(id.getPath())) {
                Item base = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path));
                if (base != Items.AIR && base != item && !COLOR.matcher(path).find()) {
                    bases.put(item, base);
                    break;
                }
            }
        }
        dyed = Set.copyOf(found);
        toBase = Map.copyOf(bases);
        builtFor = manager;
    }

    private static List<String> baseIds(String path) {
        Matcher m = COLOR.matcher(path);
        if (!m.find()) return List.of();
        String stripped = (path.substring(0, m.start()) + (m.group(1).isEmpty() || m.group(3).isEmpty() ? "" : "_") + path.substring(m.end()))
                .replaceAll("^_|_$", "");
        return List.of(stripped, stripped.replace("stained_", ""));
    }
}
