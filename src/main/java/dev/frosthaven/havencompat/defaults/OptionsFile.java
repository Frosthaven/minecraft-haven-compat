package dev.frosthaven.havencompat.defaults;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sets {@code key:value} lines in options.txt as text and leaves every other line as it is (pure part unit tested).
 * {@code Options.save()} while the game starts writes only the key mappings registered so far, which are vanilla's:
 * mods register theirs later, so every mod key binding was lost (haven-compat 0.11.0 to 0.17.0).
 */
final class OptionsFile {
    private OptionsFile() {
    }

    /** {@code lines} with each value of {@code values} in place of its key's line, or added at the end. */
    static List<String> set(List<String> lines, Map<String, String> values) {
        List<String> out = new ArrayList<>(lines);
        for (Map.Entry<String, String> value : values.entrySet()) {
            String line = value.getKey() + ":" + value.getValue();
            int at = -1;
            for (int i = 0; i < out.size() && at < 0; i++) {
                if (out.get(i).startsWith(value.getKey() + ":")) {
                    at = i;
                }
            }
            if (at < 0) {
                out.add(line);
            } else {
                out.set(at, line);
            }
        }
        return out;
    }

    static List<String> read(Path file) throws IOException {
        return Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
    }

    static void write(Path file, Map<String, String> values) throws IOException {
        Files.write(file, set(read(file), values), StandardCharsets.UTF_8);
    }
}
