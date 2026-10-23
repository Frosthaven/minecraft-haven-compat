package dev.frosthaven.havencompat.schematics;

import dev.frosthaven.havencompat.Log;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.LoadingModList;

/**
 * Create schematics and other mods' blocks. Create copies no block entity data of other mods' blocks into a print
 * (dupe protection), so AE2 cables broke at once (Create issue 5966) and every bus, pipe, machine and drawer printed
 * with factory settings. Each mod's {@link BlockData} says what is copied, charged and turned ({@link SchematicCopy});
 * {@link SavedPositions} moves links between blocks with the print; {@link ServerFill} gives a client-side save the
 * server's settings. {@link AeCableBus} and {@link AeBlockTurn} link against AE2 and load only when it is present; the
 * others work by block entity id.
 */
public final class SchematicFixes {
    private static final Log LOG = new Log("[schematics] ");

    private SchematicFixes() {
    }

    public static void register(IEventBus modBus) {
        if (!loaded("create")) {
            return;
        }
        ServerFill.register(modBus);
        if (FMLEnvironment.dist.isClient()) {
            ServerFillClient.register();
        }
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            if (loaded("ae2")) {
                run("ae2", () -> {
                    AeCableBus.register();
                    return SchematicCopy.registerNamespace(new AeSettings(), "ae2") + SchematicCopy.registerNamespace(new AeSettings(), "extendedae");
                }, "AE2 cable buses (parts, facades, settings, upgrade cards) and AE2 / ExtendedAE block settings");
            }
            run("pipez", () -> SchematicCopy.register(new PipezData(), PipezData.TYPES), "Pipez pipes (extracting and cut sides, upgrades with their filters)");
            run("mekanism", () -> SchematicCopy.registerNamespace(new MekanismData(), "mekanism") + SchematicCopy.registerNamespace(new MekanismData(), "mekanismgenerators"),
                    "Mekanism transmitter connections, machine settings, side setup and upgrades");
            run("refinedstorage", () -> SchematicCopy.registerNamespace(new RsData(), "refinedstorage"), "Refined Storage settings and upgrades");
            run("storagedrawers", () -> SchematicCopy.registerNamespace(new DrawersData(), "storagedrawers"), "Storage Drawers settings, upgrades, framing and controller links");
            run("trashcans", () -> SchematicCopy.registerNamespace(new TrashCansData(), "trashcans"), "Trash Cans filters");
            run("simplemagnets", () -> SchematicCopy.registerNamespace(new MagnetCoilData(), "simplemagnets"), "Simple Magnets coil range and filter");
        }));
    }

    private interface Registration {
        int run();
    }

    private static void run(String mod, Registration registration, String what) {
        if (!loaded(mod)) {
            return;
        }
        try {
            LOG.once(mod, "Create schematics copy {} ({} block entity types)", what, registration.run());
        } catch (Throwable t) {
            LOG.error(mod, what + " in Create schematics left as they were", t);
        }
    }

    private static boolean loaded(String mod) {
        return LoadingModList.get().getModFileById(mod) != null;
    }
}
