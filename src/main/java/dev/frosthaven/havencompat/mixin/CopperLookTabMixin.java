package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.copperchest.CopperLook;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sophisticated Storage's list of a wood storage block's variants: chests also get the copper looks; see {@link CopperLook#addTabItems}. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockBase", remap = false)
public abstract class CopperLookTabMixin {
    @Inject(method = "addCreativeTabItems", at = @At("TAIL"))
    private void haven_compat$copperLooks(Consumer<ItemStack> items, CallbackInfo ci) {
        if (getClass().getName().equals("net.p3pp3rf1y.sophisticatedstorage.block.ChestBlock")) {
            CopperLook.addTabItems((Block) (Object) this, items);
        }
    }
}
