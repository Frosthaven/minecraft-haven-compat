package dev.frosthaven.havencompat.packs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The list work of {@link OldPacks#forgetIncompatible}, pure (unit tested). */
final class IncompatibleList {
    private IncompatibleList() {
    }

    /** Removes the installed ids from {@code incompatible}, in place; returns them in list order. */
    static List<String> forget(List<String> incompatible, Predicate<String> installed) {
        List<String> gone = new ArrayList<>();
        incompatible.removeIf(id -> installed.test(id) && gone.add(id));
        return gone;
    }
}
