package dev.frosthaven.havencompat.schematics;

import dev.frosthaven.havencompat.Log;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Create schematics and AE2 (Create issue 5966): a cable, bus or terminal placed by the Schematicannon or by creative
 * printing breaks at once. {@link AeCableBus} (loaded only when both mods are present, as it links against them) tells
 * Create what to copy and what to charge.
 */
public final class SchematicFixes {
    private static final Log LOG = new Log("[schematics] ");

    private SchematicFixes() {
    }

    public static void register(IEventBus modBus) {
        if (LoadingModList.get().getModFileById("create") == null || LoadingModList.get().getModFileById("ae2") == null) {
            return;
        }
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            try {
                AeCableBus.register();
                LOG.once("ae2", "AE2 cable buses: Create schematics copy their parts and facades and charge one item for each");
            } catch (Throwable t) {
                LOG.error("ae2", "AE2 cable buses in Create schematics left as they were", t);
            }
        }));
    }
}
