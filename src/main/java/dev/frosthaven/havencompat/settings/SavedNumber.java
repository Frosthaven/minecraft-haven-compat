package dev.frosthaven.havencompat.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frosthaven.havencompat.Log;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * One whole number on the Haven Compat settings screen (a slider), saved on this computer as {@code config/<file>} with
 * one key, held between {@code min} and {@code max}. A missing or unreadable file means the default.
 */
public final class SavedNumber {
    public final int min, max;
    private final String file, key, label;
    private final Log log;
    private volatile int value;

    public SavedNumber(int fallback, int min, int max, String file, String key, String label, Log log) {
        this.value = fallback;
        this.min = min;
        this.max = max;
        this.file = file;
        this.key = key;
        this.label = label;
        this.log = log;
    }

    public int get() { return value; }

    /** Reads the saved number; call it once at mod construction. */
    public void load() {
        Path path = path();
        if (!Files.exists(path)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            value = Math.clamp(json.get(key).getAsInt(), min, max);
            log.info("{}: {}", label, value);
        } catch (Exception e) {
            log.error("read", "could not read the setting, " + value + " is used", e);
        }
    }

    /** Applies now; {@link #save} writes it (the slider saves once, when it is let go). */
    public void set(int chosen) { value = Math.clamp(chosen, min, max); }

    public void save() {
        log.info("{}: {}", label, value);
        JsonObject json = new JsonObject();
        json.addProperty(key, value);
        try {
            Files.writeString(path(), json + "\n");
        } catch (Exception e) {
            log.error("save", "could not save the setting", e);
        }
    }

    private Path path() { return FMLPaths.CONFIGDIR.get().resolve(file); }
}
