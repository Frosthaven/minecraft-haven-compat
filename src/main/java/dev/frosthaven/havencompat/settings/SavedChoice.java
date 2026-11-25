package dev.frosthaven.havencompat.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frosthaven.havencompat.Log;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * One choice on the Haven Compat settings screen, saved on this computer as {@code config/<file>} with one key. A missing
 * or unreadable file means the default.
 */
public final class SavedChoice<E extends Enum<E>> {
    private final Class<E> type;
    private final String file;
    private final String key;
    private final String label;
    private final Log log;
    private volatile E value;
    private java.util.Map<String, E> renamed = java.util.Map.of();

    public SavedChoice(Class<E> type, E fallback, String file, String key, String label, Log log) {
        this.type = type;
        this.value = fallback;
        this.file = file;
        this.key = key;
        this.label = label;
        this.log = log;
    }

    public E get() { return value; }

    /** Saved names of values that were renamed since, mapped to the value each is now. */
    public SavedChoice<E> renamed(java.util.Map<String, E> old) {
        renamed = old;
        return this;
    }

    /** Reads the saved choice; call it once at mod construction. */
    public void load() {
        Path path = path();
        if (!Files.exists(path)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            String saved = json.get(key).getAsString();
            value = renamed.containsKey(saved) ? renamed.get(saved) : Enum.valueOf(type, saved);
            log.info("{}: {}", label, value);
        } catch (Exception e) {
            log.error("read", "could not read the setting, " + value + " is used", e);
        }
    }

    /** The settings screen's choice: applies now and saves. */
    public void choose(E chosen) {
        value = chosen;
        log.info("{}: {}", label, value);
        JsonObject json = new JsonObject();
        json.addProperty(key, value.name());
        try {
            Files.writeString(path(), json + "\n");
        } catch (Exception e) {
            log.error("save", "could not save the setting", e);
        }
    }

    private Path path() { return FMLPaths.CONFIGDIR.get().resolve(file); }
}
