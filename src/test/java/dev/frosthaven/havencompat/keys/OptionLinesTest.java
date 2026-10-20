package dev.frosthaven.havencompat.keys;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionLinesTest {
    private static JsonObject spec(String value) {
        JsonObject spec = new JsonObject();
        spec.addProperty("useCommandKey", value);
        return spec;
    }

    @Test
    void theModsOwnDefaultIsReplacedOnceAndOtherLinesStay() {
        List<String> lines = new ArrayList<>(List.of("trackpadSensitivity:20.0", "useCommandKey:false", "blockCommandQQuit:true"));
        OptionLines.apply(spec("true"), new JsonObject(), lines);
        assertEquals(List.of("trackpadSensitivity:20.0", "useCommandKey:true", "blockCommandQQuit:true"), lines);
    }

    @Test
    void noFileYetGetsTheValue() {
        List<String> lines = new ArrayList<>();
        OptionLines.apply(spec("true"), new JsonObject(), lines);
        assertEquals(List.of("useCommandKey:true"), lines);
    }

    @Test
    void thePlayersLaterChoiceStays() {
        JsonObject applied = new JsonObject();
        OptionLines.apply(spec("true"), applied, new ArrayList<>());
        List<String> lines = new ArrayList<>(List.of("useCommandKey:false"));   // the player switches back to Ctrl
        assertTrue(OptionLines.apply(spec("true"), applied, lines).isEmpty());
        assertEquals(List.of("useCommandKey:false"), lines);
    }
}
