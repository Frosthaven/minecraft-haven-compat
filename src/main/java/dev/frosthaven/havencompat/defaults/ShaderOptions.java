package dev.frosthaven.havencompat.defaults;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.Os;
import dev.frosthaven.havencompat.PackDefaults;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Per-OS option values for shader packs, in Iris's per-pack option file ({@code shaderpacks/<pack>.txt}, a Java
 * properties file next to the pack). The pack's {@code shaderOptions} key:
 * <pre>{"shaderOptions": {"macos": {"ComplementaryUnbound_r5.9.zip": {"BLOCK_REFLECT_QUALITY": "1"}}}}</pre>
 * ({@code macos} on macOS, else {@code default}). A key is written when the file lacks it or still holds the value
 * written here before, so a pack change moves it; a value the player changed stays ({@link OptionChoice}). Runs at mod
 * construction, before Iris loads its pack, so the options are in place for the first pipeline and nothing reloads.
 * First use: Apple's OpenGL stops at 16 texture units, and with Create Deep Seas 3.4's hull sampler Complementary at
 * "Block Reflection Quality: High" overflows in composite1 ("No more available texture units", shaders off); Low fits.
 */
public final class ShaderOptions {
    private static final Log LOG = new Log("[pack defaults] ");

    private ShaderOptions() {
    }

    public static void apply() {
        try {
            JsonObject spec = PackDefaults.section(PackDefaults.file(), "shaderOptions");
            if (spec == null) {
                return;
            }
            DefaultsState state = DefaultsState.load();
            apply(spec, state.object("shaderOptions"), FMLPaths.GAMEDIR.get().resolve("shaderpacks"), Os.macOS() ? "macos" : "default");
            state.save();
        } catch (Throwable t) {
            LOG.error("options", "shader pack options left as they were", t);
        }
    }

    /** Applies the {@code os} section; {@code applied} records what was written. Returns true when a file changed. */
    static boolean apply(JsonObject spec, JsonObject applied, Path dir, String os) {
        if (!spec.has(os) || !spec.get(os).isJsonObject()) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, JsonElement> pack : spec.getAsJsonObject(os).entrySet()) {
            String name = pack.getKey();
            if (!pack.getValue().isJsonObject()) {
                continue;
            }
            if (!Files.exists(dir.resolve(name))) {
                LOG.once("options-missing:" + name, "shader pack {} is not in shaderpacks/; its options are skipped", name);
                continue;
            }
            Path file = dir.resolve(name + ".txt");
            try {
                Properties values = new Properties();
                if (Files.exists(file)) {
                    try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        values.load(in);
                    }
                }
                JsonObject mine = DefaultsState.object(applied, name);
                boolean packChanged = false;
                for (Map.Entry<String, JsonElement> opt : pack.getValue().getAsJsonObject().entrySet()) {
                    String key = opt.getKey(), want = opt.getValue().getAsString();
                    String have = values.getProperty(key);
                    String before = mine.has(key) ? mine.get(key).getAsString() : null;
                    switch (OptionChoice.decide(have, before, want)) {
                        case ALREADY -> mine.addProperty(key, want);
                        case PLAYERS -> LOG.once("options-player:" + name + ":" + key, "{}: {}={} is the player's choice; left alone", name, key, have);
                        case WRITE -> {
                            values.setProperty(key, want);
                            mine.addProperty(key, want);
                            packChanged = true;
                            LOG.once("options:" + name + ":" + key, "{} ({}): {} {} -> {}", name, os, key, have == null ? "unset" : have, want);
                        }
                    }
                }
                if (packChanged) {
                    try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                        values.store(out, "Shader pack options; the Haven pack defaults set some of these once");
                    }
                    changed = true;
                }
            } catch (Exception e) {
                LOG.error("options:" + name, "could not update " + file.getFileName() + "; skipped", e);
            }
        }
        return changed;
    }
}
