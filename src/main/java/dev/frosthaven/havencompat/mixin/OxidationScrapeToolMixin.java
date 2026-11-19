package dev.frosthaven.havencompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create: Oxidation 1.0.0 scrapes (and takes wax off) only with an item that {@code instanceof AxeItem}; tools that
 * are no AxeItem but do an axe's work (Mekanism's paxels, other multi-tools) were refused, while vanilla copper asks
 * the item ({@code ItemAbilities.AXE_SCRAPE} / {@code AXE_WAX_OFF}). This makes the mod ask the same question.
 */
@Pseudo
@Mixin(targets = "dev.mrmulle.createoxidized.neoforge.CreateOxidizedNeoForge", remap = false)
public abstract class OxidationScrapeToolMixin {
    @ModifyExpressionValue(method = "onRightClickBlock",
            at = @At(value = "CONSTANT", args = "classValue=net/minecraft/world/item/AxeItem"))
    private boolean haven$anyScrapingTool(boolean isAxe,
            @Local(argsOnly = true) PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        return isAxe || stack.canPerformAction(ItemAbilities.AXE_SCRAPE) || stack.canPerformAction(ItemAbilities.AXE_WAX_OFF);
    }
}
