package dev.frosthaven.havencompat.defaults;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A key map a player drops into the game folder: {@code haven-keys.txt}, lines as options.txt stores keys
 * ({@code key_key.mekanism.mode:key.mouse.4}, {@code key_key.mekanism.head_mode:key.keyboard.6:ALT}). Applied with the
 * pack defaults while the game starts ({@link LoadDefaults}, as text: those key lines replaced or added, every other
 * line left as it is), after the pack's key moves, so the file wins. Blank lines and lines starting with {@code #} are
 * skipped, and so is every line not starting with {@code key_}. Then the file is renamed {@code haven-keys.applied.txt}
 * (an older one is replaced), so it applies once.
 */
final class KeymapImport {
    static final String FILE = "haven-keys.txt";
    static final String APPLIED = "haven-keys.applied.txt";

    private KeymapImport() {
    }

    /** The key lines of {@code lines}, name to value, in file order (a later line for the same key wins). */
    static Map<String, String> parse(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || !line.startsWith("key_")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= "key_".length() || colon == line.length() - 1) {
                continue;   // no value
            }
            out.put(line.substring(0, colon), line.substring(colon + 1));
        }
        return out;
    }

    /** The import file's keys, or an empty map when the game folder has none. */
    static Map<String, String> read(Path gameDir) throws IOException {
        Path file = gameDir.resolve(FILE);
        return Files.exists(file) ? parse(Files.readAllLines(file, StandardCharsets.UTF_8)) : Map.of();
    }

    /** Renames the import file once its keys are in options.txt. */
    static void done(Path gameDir) throws IOException {
        Files.move(gameDir.resolve(FILE), gameDir.resolve(APPLIED), StandardCopyOption.REPLACE_EXISTING);
    }
}
