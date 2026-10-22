package dev.frosthaven.havencompat.defaults;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionsFileTest {
    @Test
    void onlyTheGivenLinesChangeAndModKeyBindingsStay() {
        List<String> lines = List.of("version:3955", "resourcePacks:[\"vanilla\"]", "key_key.create.rotate_menu:key.keyboard.left.alt",
                "darkMojangStudiosBackground:false", "key_key.jei.showRecipe:key.keyboard.r");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("resourcePacks", "[\"vanilla\",\"file/haven-fixes\"]");
        values.put("darkMojangStudiosBackground", "true");
        assertEquals(List.of("version:3955", "resourcePacks:[\"vanilla\",\"file/haven-fixes\"]", "key_key.create.rotate_menu:key.keyboard.left.alt",
                "darkMojangStudiosBackground:true", "key_key.jei.showRecipe:key.keyboard.r"), OptionsFile.set(lines, values));
    }

    @Test
    void aMissingLineIsAdded() {
        assertEquals(List.of("version:3955", "darkMojangStudiosBackground:true"),
                OptionsFile.set(List.of("version:3955"), Map.of("darkMojangStudiosBackground", "true")));
    }
}
