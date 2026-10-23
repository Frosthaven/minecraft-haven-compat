package dev.frosthaven.havencompat.packs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncompatibleListTest {
    @Test
    void installedPacksLeaveTheIncompatibleListAndMissingOnesStay() {
        List<String> incompatible = new ArrayList<>(List.of("file/redstone-3d Numbered.zip", "file/gone.zip"));
        List<String> gone = IncompatibleList.forget(incompatible, Set.of("file/redstone-3d Numbered.zip")::contains);
        assertEquals(List.of("file/redstone-3d Numbered.zip"), gone);
        assertEquals(List.of("file/gone.zip"), incompatible);
    }
}
