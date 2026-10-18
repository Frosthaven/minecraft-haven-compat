package dev.frosthaven.havencompat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The pack's {@code config/haven-pack-defaults.json} (packwiz-managed, so a change reaches every instance on its next
 * launch): one top-level key per concern. The fixes here read their own key; haven-create-ui-ux reads
 * {@code resourcePacks} and {@code shader}. Unknown keys are ignored by everyone, so a key can ship before its reader.
 */
public final class PackDefaults {
    public static final String FILE = "haven-pack-defaults.json";

    private PackDefaults() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    /** The object under {@code key}, or null when the file or the key is absent. */
    public static JsonObject section(Path file, String key) throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    /** The whole file, or null when absent. */
    public static JsonObject root(Path file) throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
