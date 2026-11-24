package dev.frosthaven.havencompat.defaults;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeymapImportTest {
    @Test
    void onlyKeyLinesWithAValueCount() {
        Map<String, String> keys = KeymapImport.parse(List.of(
                "# my keys", "", "  key_key.mekanism.mode:key.mouse.4  ", "key_key.mekanism.head_mode:key.keyboard.6:ALT",
                "fov:0.5", "key_key.jump:", "nonsense"));
        assertEquals(Map.of("key_key.mekanism.mode", "key.mouse.4", "key_key.mekanism.head_mode", "key.keyboard.6:ALT"), keys);
    }

    @Test
    void importedKeysReplaceTheirLinesOnly() {
        List<String> options = List.of("version:3955", "key_key.mekanism.mode:key.keyboard.n", "fov:0.5");
        assertEquals(List.of("version:3955", "key_key.mekanism.mode:key.mouse.4", "fov:0.5", "key_key.new:key.keyboard.j"),
                OptionsFile.set(options, KeymapImport.parse(List.of("key_key.mekanism.mode:key.mouse.4", "key_key.new:key.keyboard.j"))));
    }
}
