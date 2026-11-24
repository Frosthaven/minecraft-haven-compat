package dev.frosthaven.havencompat.ntgl;

import java.util.ArrayList;
import java.util.List;

/** Which entries of NTGL's reload and unload wheel stay ({@link NtglReloadWheel}). Pure (unit tested). */
public final class WheelEntries {
    private WheelEntries() {}

    /**
     * Indices of the entries to keep: the first entry of each mode that uses ammo. modes.get(i) is entry i's mode
     * (null: unknown, kept). When no entry would stay, all stay.
     */
    public static List<Integer> kept(List<?> modes, List<Boolean> usesAmmo) {
        List<Integer> keep = new ArrayList<>();
        List<Object> seen = new ArrayList<>();
        for (int i = 0; i < modes.size(); i++) {
            Object mode = modes.get(i);
            if (mode != null && (!usesAmmo.get(i) || seen.contains(mode))) continue;
            if (mode != null) seen.add(mode);
            keep.add(i);
        }
        if (keep.isEmpty()) for (int i = 0; i < modes.size(); i++) keep.add(i);
        return keep;
    }
}
