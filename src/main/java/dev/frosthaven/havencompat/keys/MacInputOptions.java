package dev.frosthaven.havencompat.keys;

import com.google.gson.JsonObject;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.Os;
import dev.frosthaven.havencompat.PackDefaults;
import dev.frosthaven.havencompat.defaults.DefaultsState;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * macOS only: the pack's option values for MacOS Input Fixes, in its {@code config/macos_input_fixes.txt}
 * ({@code key:value} lines, read at client setup, so this runs at mod construction, before it). The pack's
 * {@code macosInputFixes} key:
 * <pre>{"macosInputFixes": {"useCommandKey": "true"}}</pre>
 * (Cmd for game shortcuts: Cmd+C / Cmd+V in chat, Cmd + drop for the whole stack, Cmd+Q stays blocked). The first time,
 * a value is written whatever the file holds (the mod writes its own defaults there); after that, a value the player
 * changed stays and a pack change moves the value written here ({@link OptionLines}).
 */
public final class MacInputOptions {
    static final String FILE = "macos_input_fixes.txt";
    private static final Log LOG = new Log("[mac keys] ");

    private MacInputOptions() {
    }

    public static void apply() {
        if (!Os.macOS() || LoadingModList.get().getModFileById(MacInputLogs.MOD) == null) {
            return;
        }
        try {
            JsonObject spec = PackDefaults.section(PackDefaults.file(), "macosInputFixes");
            if (spec == null) {
                return;
            }
            DefaultsState state = DefaultsState.load();
            apply(spec, state.object("macosInputFixes"), FMLPaths.CONFIGDIR.get().resolve(FILE));
            state.save();
        } catch (Throwable t) {
            LOG.error("options", "MacOS Input Fixes options left as they were", t);
        }
    }

    /** Applies {@code spec} to {@code file} ({@link OptionLines}); {@code applied} records what was written. */
    static void apply(JsonObject spec, JsonObject applied, Path file) throws IOException {
        List<String> lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8)) : new ArrayList<>();
        List<String> written = OptionLines.apply(spec, applied, lines);
        if (!written.isEmpty()) {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
            LOG.once("options", "MacOS Input Fixes options set by the pack: {}", String.join(", ", written));
        }
    }
}
