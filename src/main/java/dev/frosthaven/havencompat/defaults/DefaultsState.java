package dev.frosthaven.havencompat.defaults;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/haven-pack-defaults.state.json}: what the pack defaults already applied on this instance, so a
 * default is applied once and the player's later choice stays. Keys: {@code seen} (resource packs seen installed),
 * {@code shaderApplied} (the first-run shader pack), {@code shaderOptions} (per pack, the option values written), {@code ordered} (resource pack order rules applied), {@code darkLoadingScreen} (Monochrome Logo switched on), {@code keys} (key binding moves decided).
 * The same file and keys as haven-create-ui-ux kept up to 1.3.0, so an existing install applies nothing again.
 */
public final class DefaultsState {
    public static final String FILE = "haven-pack-defaults.state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final JsonObject root;

    private DefaultsState(Path path, JsonObject root) {
        this.path = path;
        this.root = root;
    }

    public static DefaultsState load() throws IOException {
        return load(FMLPaths.CONFIGDIR.get().resolve(FILE));
    }

    static DefaultsState load(Path path) throws IOException {
        JsonObject root = Files.exists(path)
                ? JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        return new DefaultsState(path, root);
    }

    public JsonObject root() {
        return root;
    }

    /** The object under {@code key}, created when absent. */
    public JsonObject object(String key) {
        return object(root, key);
    }

    public static JsonObject object(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            parent.add(key, new JsonObject());
        }
        return parent.getAsJsonObject(key);
    }

    public void save() throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
    }
}
