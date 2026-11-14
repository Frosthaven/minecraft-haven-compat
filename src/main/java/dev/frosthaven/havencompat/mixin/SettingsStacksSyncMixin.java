package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.Log;
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sophisticated Core sends a storage's full contents in its own packet when the storage has a stack upgrade (slots
 * over one stack), for the storage screen and for the settings screen alike. The client handler applies it only to a
 * {@code StorageContainerMenuBase}; the settings menu is not one (the single-slot handler, {@code SyncSlotStackPayload},
 * does accept it), so the packet was dropped and the settings screen drew the client's last known contents until Back
 * and Settings again. The same packet is now applied to an open settings menu too, as vanilla's contents packet is
 * for a storage without a stack upgrade. Backpacks and Sophisticated Storage share the handler.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.network.SyncContainerStacksPayload", remap = false)
public abstract class SettingsStacksSyncMixin {
    @Unique
    private static final Log haven_compat$LOG = new Log("[storage settings] ");

    @Shadow public abstract int windowId();

    @Shadow public abstract int stateId();

    @Shadow public abstract List<ItemStack> itemStacks();

    @Shadow public abstract ItemStack carriedStack();

    @Inject(method = "handlePayload", at = @At("HEAD"))
    private static void haven_compat$settingsMenu(@Coerce Object payload, IPayloadContext context, CallbackInfo ci) {
        SettingsStacksSyncMixin stacks = (SettingsStacksSyncMixin) payload;
        AbstractContainerMenu menu = context.player().containerMenu;
        if (menu.containerId == stacks.windowId() && haven_compat$isSettingsMenu(menu)) {
            menu.initializeContents(stacks.stateId(), stacks.itemStacks(), stacks.carriedStack());
            haven_compat$LOG.once("applied", "a stack-upgraded storage's contents reach its settings screen");
        }
    }

    @Unique
    private static boolean haven_compat$isSettingsMenu(AbstractContainerMenu menu) {
        for (Class<?> c = menu.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("net.p3pp3rf1y.sophisticatedcore.common.gui.SettingsContainerMenu")) {
                return true;
            }
        }
        return false;
    }
}
