package dev.frosthaven.havencompat;

import dev.frosthaven.havencompat.binder.BinderSortMessage;
import dev.frosthaven.havencompat.schematics.SchematicFixes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The fixes that run on both sides (a dedicated server builds this class, not {@link HavenCompat}):
 *  - {@code schematics}: Create schematics keep other mods' block settings and upgrades, charge for them, turn them
 *    and move their links with the print; AE2 cables print again ({@link SchematicFixes}).
 *  - {@code binder}: the server applies a card binder sort ({@link BinderSortMessage}); it does nothing without a binder open.
 */
@Mod(HavenCompat.ID)
public final class HavenCompatCommon {
    public HavenCompatCommon(IEventBus modBus) {
        SchematicFixes.register(modBus);
        BinderSortMessage.register(modBus);
    }
}
