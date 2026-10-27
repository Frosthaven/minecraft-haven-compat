package dev.frosthaven.havencompat.defaults;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Key binding moves ({@code keys} in haven-pack-defaults.json): each rule moves one options.txt key line once, from the
 * old default {@code from} to {@code to}, and only while the player still has {@code from} (or no line). A key the
 * player bound elsewhere stays ({@link OptionChoice}). State {@code keys}: one entry per rule applied or skipped.
 * <pre>
 * {"keys": [{"key": "key_justzoom.keybinds.keybind.zoom", "from": "key.keyboard.z", "to": "key.keyboard.y"}]}
 * </pre>
 */
final class KeyDefaults {
    private KeyDefaults() {
    }

    /** The options.txt values to write; marks every rule it decided in {@code done}. */
    static Map<String, String> moves(List<String> lines, JsonArray rules, JsonObject done) {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonElement el : rules) {
            JsonObject rule = el.getAsJsonObject();
            String key = rule.get("key").getAsString();
            String to = rule.get("to").getAsString();
            String id = key + " -> " + to;
            if (done.has(id)) {
                continue;
            }
            if (OptionChoice.decide(value(lines, key), rule.get("from").getAsString(), to) == OptionChoice.WRITE) {
                out.put(key, to);
            }
            done.addProperty(id, true);
        }
        return out;
    }

    /** The value of {@code key}'s line, or null when options.txt has none. */
    static String value(List<String> lines, String key) {
        for (String line : lines) {
            if (line.startsWith(key + ":")) {
                return line.substring(key.length() + 1);
            }
        }
        return null;
    }
}
