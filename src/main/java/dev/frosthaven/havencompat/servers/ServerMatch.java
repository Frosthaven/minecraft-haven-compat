package dev.frosthaven.havencompat.servers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides what to change in a player's server list so the pack's servers carry their current address. Pure: no
 * Minecraft classes, so it is unit tested.
 */
public final class ServerMatch {
    /** A server the pack declares: its entry name, its address now, and the addresses it had before. */
    public record PackServer(String name, String address, List<String> previous) {
    }

    /** A list entry as the player has it. */
    public record Entry(String name, String address) {
    }

    public enum Kind { SET, REMOVE, ADD }

    /** SET: entry {@code index} gets {@code address}. REMOVE: entry {@code index} goes (a duplicate). ADD: a new entry. */
    public record Change(Kind kind, int index, String name, String address) {
    }

    private ServerMatch() {
    }

    /**
     * The changes, in the order to apply them: sets, then removes (highest index first), then adds. An entry is the
     * pack's server when its address is the server's current or a previous address, or it carries the server's entry
     * name. Two entries that end up as the same name and address keep the first. No entry at all: the server is added.
     */
    public static List<Change> plan(List<Entry> have, List<PackServer> want) {
        List<Change> sets = new ArrayList<>(), removes = new ArrayList<>(), adds = new ArrayList<>();
        List<Entry> after = new ArrayList<>(have);
        for (PackServer server : want) {
            boolean found = false;
            for (int i = 0; i < have.size(); i++) {
                Entry entry = have.get(i);
                if (!same(server, entry)) {
                    continue;
                }
                found = true;
                if (!sameAddress(entry.address(), server.address())) {
                    sets.add(new Change(Kind.SET, i, entry.name(), server.address()));
                    after.set(i, new Entry(entry.name(), server.address()));
                }
            }
            if (!found) {
                adds.add(new Change(Kind.ADD, -1, server.name(), server.address()));
            }
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < after.size(); i++) {
            Entry entry = after.get(i);
            if (!seen.add(entry.name().trim().toLowerCase(Locale.ROOT) + "\n" + normalize(entry.address()))) {
                removes.add(0, new Change(Kind.REMOVE, i, entry.name(), entry.address()));
            }
        }
        List<Change> all = new ArrayList<>(sets);
        all.addAll(removes);
        all.addAll(adds);
        return all;
    }

    static boolean same(PackServer server, Entry entry) {
        if (server.name().trim().equalsIgnoreCase(entry.name().trim())) {
            return true;
        }
        if (sameAddress(entry.address(), server.address())) {
            return true;
        }
        for (String previous : server.previous()) {
            if (sameAddress(entry.address(), previous)) {
                return true;
            }
        }
        return false;
    }

    static boolean sameAddress(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    /** Lower case, trimmed, and without the default port (the game treats {@code host} and {@code host:25565} alike). */
    static String normalize(String address) {
        String a = address.trim().toLowerCase(Locale.ROOT);
        return a.endsWith(":25565") ? a.substring(0, a.length() - ":25565".length()) : a;
    }
}
