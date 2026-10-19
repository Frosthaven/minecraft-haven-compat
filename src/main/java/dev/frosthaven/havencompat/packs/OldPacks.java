package dev.frosthaven.havencompat.packs;

import dev.frosthaven.havencompat.Log;

/**
 * Resource packs made for an older (or newer) pack format count as compatible on the client: no red "made for an older
 * version" entry, no confirmation when the player turns one on, and {@code Options.loadSelectedResourcePacks} keeps an
 * outdated pack the pack defaults enabled (vanilla drops it unless it is in {@code incompatibleResourcePacks}). Many
 * packs work across formats but bump the number rarely (3D redstone wires: format 9). The same as the Fabric mod
 * "No Resource Pack Warnings", whose NeoForge port excludes 1.21.1.
 */
public final class OldPacks {
    private static final Log LOG = new Log("[old packs] ");

    private OldPacks() {
    }

    /** The value the patched {@code PackCompatibility.isCompatible()} calls see. */
    public static boolean compatible(boolean actual) {
        if (!actual) {
            LOG.once("on", "resource packs for another pack format are treated as compatible");
        }
        return true;
    }
}
