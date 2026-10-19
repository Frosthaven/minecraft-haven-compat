package dev.frosthaven.havencompat.defaults;

import java.util.List;

/**
 * One order fix for the selected resource packs (bottom, lowest priority, first): {@code id} goes right above
 * {@code anchor} when it is below it. Both must be selected; otherwise nothing changes.
 */
public final class PackOrder {
    private PackOrder() {
    }

    /** True when both packs are selected, so the rule is settled (moved now, or already above). */
    public static boolean settled(List<String> selected, String id, String anchor) {
        return selected.contains(id) && selected.contains(anchor);
    }

    /** Moves {@code id} right above {@code anchor} if it sits below it; returns whether the list changed. */
    public static boolean moveAbove(List<String> selected, String id, String anchor) {
        if (!settled(selected, id, anchor) || selected.indexOf(id) > selected.indexOf(anchor)) {
            return false;
        }
        selected.remove(id);
        selected.add(selected.indexOf(anchor) + 1, id);
        return true;
    }
}
