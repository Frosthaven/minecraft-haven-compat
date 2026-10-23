package dev.frosthaven.havencompat.schematics;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;

/**
 * The items some recipe of the running server makes (crafting, smithing, any mod's machines). A Schematicannon charge
 * for any other item (a creative upgrade, an item a pack removed the recipe of) could never be paid, so the print would
 * wait forever: such items are not copied and not charged. Built once per recipe manager (a data reload makes a new one).
 */
final class Obtainable {
    private static RecipeManager builtFor;
    private static Set<Item> items = Set.of();

    private Obtainable() {
    }

    static synchronized boolean test(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        RecipeManager recipes = server.getRecipeManager();
        if (recipes != builtFor) {
            RegistryAccess registries = server.registryAccess();
            Set<Item> made = new HashSet<>();
            for (RecipeHolder<?> recipe : recipes.getRecipes()) {
                try {
                    ItemStack result = recipe.value().getResultItem(registries);
                    if (!result.isEmpty()) {
                        made.add(result.getItem());
                    }
                } catch (RuntimeException ignored) {
                    // a mod recipe without a fixed result (or one that needs a level): it makes nothing we charge
                }
            }
            items = made;
            builtFor = recipes;
        }
        return items.contains(stack.getItem());
    }
}
