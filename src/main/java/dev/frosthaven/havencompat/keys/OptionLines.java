package dev.frosthaven.havencompat.keys;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.defaults.OptionChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The pack's values in a {@code key:value} options file (MacOS Input Fixes), pure (unit tested). The first time a key
 * is set, the file's value counts as the mod's own default and is replaced; after that {@link OptionChoice} decides
 * with the value recorded in {@code applied}, so a value the player changed stays.
 */
final class OptionLines {
    private OptionLines() {
    }

    /** Edits {@code lines} in place and records each pack value in {@code applied}. Returns the {@code key:value} lines written. */
    static List<String> apply(JsonObject spec, JsonObject applied, List<String> lines) {
        List<String> written = new ArrayList<>();
        for (Map.Entry<String, JsonElement> opt : spec.entrySet()) {
            String key = opt.getKey(), want = opt.getValue().getAsString();
            int at = line(lines, key);
            String have = at < 0 ? null : lines.get(at).substring(key.length() + 1);
            String before = applied.has(key) ? applied.get(key).getAsString() : have;   // first time: the mod's own value
            OptionChoice choice = OptionChoice.decide(have, before, want);
            if (choice == OptionChoice.PLAYERS) {
                continue;
            }
            if (choice == OptionChoice.WRITE) {
                if (at < 0) {
                    lines.add(key + ":" + want);
                } else {
                    lines.set(at, key + ":" + want);
                }
                written.add(key + ":" + want);
            }
            applied.addProperty(key, want);
        }
        return written;
    }

    private static int line(List<String> lines, String key) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + ":")) {
                return i;
            }
        }
        return -1;
    }
}
