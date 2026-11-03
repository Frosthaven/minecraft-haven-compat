package dev.frosthaven.havencompat.binder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.binder.CardView.Sort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/** The player's sort choice, kept in {@code config/haven-compat-card-binder.json} on this computer. */
final class BinderSort {
    private static final Log LOG = new Log("[card binder] ");
    private static Sort current;

    private BinderSort() {}

    static Sort get() {
        if (current == null) current = read();
        return current;
    }

    static void set(Sort sort) {
        current = sort;
        JsonObject json = new JsonObject();
        json.addProperty("sort", sort.name());
        try {
            Files.writeString(file(), json + "\n");
        } catch (IOException e) {
            LOG.error("save", "could not save the sort choice", e);
        }
    }

    private static Sort read() {
        Path file = file();
        if (!Files.exists(file)) return Sort.NAME_AZ;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return Sort.parse(json.has("sort") ? json.get("sort").getAsString() : null);
        } catch (Exception e) {
            LOG.error("read", "could not read the sort choice, Name A-Z is used", e);
            return Sort.NAME_AZ;
        }
    }

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("haven-compat-card-binder.json"); }
}
