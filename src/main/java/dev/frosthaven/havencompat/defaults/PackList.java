package dev.frosthaven.havencompat.defaults;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The pack's {@code resourcePacks} list applied to the selected resource packs (bottom, lowest priority, first): a
 * listed pack is enabled when it is first seen installed, or when it is installed again after it was removed;
 * "above" / "below" place it right above or below another selected pack. A pack the player turned off while it stayed
 * installed is never turned back on ({@code seen}: pack id -> installed when last looked at).
 */
public final class PackList {
    private PackList() {
    }

    /** Enables the newly seen packs in {@code selected}; returns what was enabled, for the log (empty: no change). */
    public static List<String> enable(List<String> selected, Collection<String> available, JsonArray list, JsonObject seen) {
        List<String> enabled = new ArrayList<>();
        for (JsonElement el : list) {
            JsonObject entry = el.getAsJsonObject();
            String id = entry.get("id").getAsString();
            boolean present = available.contains(id);
            boolean wasSeen = seen.has(id) && seen.get(id).getAsBoolean();
            seen.addProperty(id, present);
            if (!present || wasSeen || selected.contains(id)) {
                continue;   // not installed, installed before (the player's choice stands), or already on
            }
            int at = selected.size();
            String anchor = entry.has("above") ? entry.get("above").getAsString() : entry.has("below") ? entry.get("below").getAsString() : null;
            if (anchor != null && selected.contains(anchor)) {
                at = selected.indexOf(anchor) + (entry.has("above") ? 1 : 0);
            }
            selected.add(at, id);
            enabled.add(anchor == null ? id : id + (entry.has("above") ? " above " : " below ") + anchor);
        }
        return enabled;
    }
}
