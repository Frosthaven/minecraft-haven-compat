package dev.frosthaven.havencompat.packs;

import dev.frosthaven.havencompat.Log;

import java.util.List;
import java.util.function.Predicate;

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

    /**
     * Takes the installed packs off {@code incompatibleResourcePacks} before {@code Options.loadSelectedResourcePacks}
     * reads it. Vanilla takes a listed pack that is now compatible off that list, but in that same branch never adds it
     * to the selected packs, and the shorter list reaches options.txt only at the next options save. So a pack the
     * player once confirmed as outdated stayed on in options.txt and never loaded, launch after launch (3D redstone
     * wires, 2026-10-23). Every pack is compatible here, so with its entry gone vanilla keeps it like any other.
     * {@code installed} says whether the repository has the pack; a missing one stays listed (vanilla drops it from
     * the selection itself). Returns the ids taken off.
     */
    public static List<String> forgetIncompatible(List<String> incompatible, Predicate<String> installed) {
        List<String> gone = IncompatibleList.forget(incompatible, installed);
        if (!gone.isEmpty()) LOG.once("forgot", "kept outdated packs that vanilla would drop at start: {}", gone);
        return gone;
    }
}
