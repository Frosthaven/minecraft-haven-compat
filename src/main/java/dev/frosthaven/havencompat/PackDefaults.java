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
 * launch): one top-level key per concern, each fix reads its own key ({@code servers}, {@code jade}, and the
 * {@code defaults} package: {@code resourcePacks}, {@code shader}, {@code shaderOptions}). Unknown keys are ignored, so a
 * key can ship before its reader. This mod is the file's only reader since 0.5.0.
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
