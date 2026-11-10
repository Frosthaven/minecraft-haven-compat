package dev.frosthaven.havencompat.dyeing;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

/**
 * Where a Controlled Fan Dyeing Catalyst puts the dye in the crafting grid it builds for an item. Recipes that read
 * the dye's place give different results: Sophisticated Backpacks colours the main body for a dye left of the
 * backpack, the trim for one right of it, and both for one in the same column.
 */
public enum DyePosition implements StringRepresentable {
    LEFT("left", "L"), RIGHT("right", "R"), TOP("top", "T"), BOTTOM("bottom", "B"), SURROUND("surround", "S");

    private final String name;
    private final String letter;

    DyePosition(String name, String letter) {
        this.name = name;
        this.letter = letter;
    }

    @Override public String getSerializedName() { return name; }

    /** The one character Create's value box shows on the nozzle. */
    public String letter() { return letter; }

    public DyePosition next() { return values()[(ordinal() + 1) % values().length]; }

    /** The crafting grid: one item, and the dye in this place (all 8 cells around the item for SURROUND). */
    public CraftingInput grid(ItemStack item, ItemStack dye) {
        return switch (this) {
            case LEFT -> CraftingInput.of(2, 1, List.of(dye, item));
            case RIGHT -> CraftingInput.of(2, 1, List.of(item, dye));
            case TOP -> CraftingInput.of(1, 2, List.of(dye, item));
            case BOTTOM -> CraftingInput.of(1, 2, List.of(item, dye));
            case SURROUND -> {
                NonNullList<ItemStack> cells = NonNullList.withSize(9, dye);
                cells.set(4, item);
                yield CraftingInput.of(3, 3, cells);
            }
        };
    }
}
