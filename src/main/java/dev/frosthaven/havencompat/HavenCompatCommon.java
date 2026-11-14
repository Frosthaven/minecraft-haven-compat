package dev.frosthaven.havencompat;

import dev.frosthaven.havencompat.binder.BinderSortMessage;
import dev.frosthaven.havencompat.schematics.SchematicFixes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/**
 * The fixes that run on both sides (a dedicated server builds this class, not {@link HavenCompat}):
 *  - {@code schematics}: Create schematics keep other mods' block settings and upgrades, charge for them, turn them
 *    and move their links with the print; AE2 cables print again ({@link SchematicFixes}).
 *  - {@code spells}: the server aims Ars Nouveau spell book clicks at the mob the client clicked ({@code TouchTargets}).
 *  - {@code binder}: the server applies a card binder sort ({@link BinderSortMessage}); it does nothing without a binder open.
 *  - {@code dyeing}: the Brass Dye Nozzle and Controlled Fan Dyeing Catalysts (bulk dyeing with the dye placed left,
 *    right, top, bottom or all around the item), with Create, Create Connected and Create Dragons Plus loaded.
 *  - {@code bleach}: the Bleach fluid and bulk bleaching (a fan through bleach takes dye off, or turns an item white), with Create loaded.
 *  - {@code copperchest}: Haven Backports' Copper Chest as a look of Sophisticated Storage's chests: tier upgrades work on
 *    it and the chest keeps aging, waxing and scraping ({@code CopperLook}), with both mods loaded.
 */
@Mod(HavenCompat.ID)
public final class HavenCompatCommon {
    public HavenCompatCommon(IEventBus modBus) {
        SchematicFixes.register(modBus);
        BinderSortMessage.register(modBus);
        if (ModList.get().isLoaded("ars_nouveau")) {
            dev.frosthaven.havencompat.spells.TouchTargetMessage.register(modBus);
        }
        if (ModList.get().isLoaded("create")) {
            dev.frosthaven.havencompat.bleach.Bleach.register(modBus);
            if (ModList.get().isLoaded("create_connected")) {
                dev.frosthaven.havencompat.bleach.BleachCatalyst.register(modBus);
            }
        }
        if (ModList.get().isLoaded("sophisticatedstorage") && ModList.get().isLoaded("haven_backports")) {
            dev.frosthaven.havencompat.copperchest.CopperLook.register(modBus);
        }
        if (ModList.get().isLoaded("create") && ModList.get().isLoaded("create_connected") && ModList.get().isLoaded("create_dragons_plus")) {
            dev.frosthaven.havencompat.dyeing.Dyeing.register(modBus);
        }
    }
}
