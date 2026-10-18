package dev.frosthaven.havencompat.updates;

import dev.frosthaven.havencompat.Log;

/**
 * Mods' own update checks are off in the pack. The pack manages every mod's version (packwiz), so a mod's
 * "update available" screen asks the player for something they cannot do, and it nags about builds the pack does not
 * take: Create Deep Seas showed its alpha 3.4.1 to every player on its release 3.3.0, in front of the title screen.
 * One mixin per mod, each applied only when that mod is installed ({@code CompatMixinPlugin}); this is their shared log.
 */
public final class UpdateNags {
    /** Create Deep Seas: {@code check()} fetches Modrinth and arms {@code DeepSeasUpdateScreen}; cancelled at its start. */
    public static final String DEEP_SEAS_TARGET = "com.maxenonyme.createsubmarine.submarine.system.UpdateChecker";
    private static final Log LOG = new Log("[update nags] ");

    private UpdateNags() {
    }

    /** A mixin calls this in place of the mod's check. */
    public static void skipped(String mod) {
        LOG.once(mod, "{}: its own update check is off (the pack manages mod versions)", mod);
    }
}
