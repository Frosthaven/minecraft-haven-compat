package dev.frosthaven.havencompat.defaults;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyDefaultsTest {
    private static final JsonArray ZOOM = JsonParser.parseString(
            "[{\"key\": \"key_justzoom.keybinds.keybind.zoom\", \"from\": \"key.keyboard.z\", \"to\": \"key.keyboard.y\"}]").getAsJsonArray();

    @Test
    void theOldDefaultMovesOnce() {
        JsonObject done = new JsonObject();
        List<String> lines = List.of("version:3955", "key_justzoom.keybinds.keybind.zoom:key.keyboard.z");
        assertEquals(Map.of("key_justzoom.keybinds.keybind.zoom", "key.keyboard.y"), KeyDefaults.moves(lines, ZOOM, done));
        assertTrue(done.has("key_justzoom.keybinds.keybind.zoom -> key.keyboard.y"));
        assertEquals(Map.of(), KeyDefaults.moves(lines, ZOOM, done));   // a player who moves it back to Z keeps Z
    }

    @Test
    void aKeyThePlayerBoundStays() {
        JsonObject done = new JsonObject();
        assertEquals(Map.of(), KeyDefaults.moves(List.of("key_justzoom.keybinds.keybind.zoom:key.keyboard.c"), ZOOM, done));
        assertTrue(done.has("key_justzoom.keybinds.keybind.zoom -> key.keyboard.y"));
    }

    @Test
    void aMissingLineGetsTheNewKey() {
        assertEquals(Map.of("key_justzoom.keybinds.keybind.zoom", "key.keyboard.y"),
                KeyDefaults.moves(List.of("version:3955"), ZOOM, new JsonObject()));
    }
}
