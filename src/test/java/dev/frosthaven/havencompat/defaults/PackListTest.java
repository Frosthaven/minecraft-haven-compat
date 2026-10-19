package dev.frosthaven.havencompat.defaults;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackListTest {
    private static final JsonArray LIST = JsonParser.parseString(
            "[{\"id\": \"fa\"}, {\"id\": \"ext\", \"above\": \"fa\"}, {\"id\": \"wires\"}]").getAsJsonArray();

    @Test
    void aNewlyInstalledPackIsEnabledAboveItsAnchor() {
        List<String> selected = new ArrayList<>(List.of("vanilla", "fa", "ve"));
        JsonObject seen = JsonParser.parseString("{\"fa\": true}").getAsJsonObject();
        assertEquals(List.of("ext above fa", "wires"), PackList.enable(selected, List.of("vanilla", "fa", "ve", "ext", "wires"), LIST, seen));
        assertEquals(List.of("vanilla", "fa", "ext", "ve", "wires"), selected);
    }

    @Test
    void aPackThePlayerTurnedOffStaysOff() {
        List<String> selected = new ArrayList<>(List.of("vanilla", "fa", "ext"));
        JsonObject seen = JsonParser.parseString("{\"fa\": true, \"ext\": true, \"wires\": true}").getAsJsonObject();
        assertTrue(PackList.enable(selected, List.of("vanilla", "fa", "ext", "wires"), LIST, seen).isEmpty());
        assertEquals(List.of("vanilla", "fa", "ext"), selected);
    }

    @Test
    void aPackInstalledAgainAfterItWasRemovedIsEnabled() {
        List<String> selected = new ArrayList<>(List.of("vanilla", "fa", "ext"));
        JsonObject seen = JsonParser.parseString("{\"fa\": true, \"ext\": true, \"wires\": true}").getAsJsonObject();
        PackList.enable(selected, List.of("vanilla", "fa", "ext"), LIST, seen);   // removed: seen as absent
        assertEquals(List.of("wires"), PackList.enable(selected, List.of("vanilla", "fa", "ext", "wires"), LIST, seen));
    }
}
