package dev.frosthaven.havencompat.trees;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class HangingLogsTest {
    record Pos(int x, int y, int z) {
        Pos below() { return new Pos(x, y - 1, z); }
    }

    /** A branch as Ars places it: up the trunk side, out to the tip, then one log under the tip. */
    @Test
    void takesTheLogUnderABranchTipOnce() {
        Pos tip = new Pos(3, 72, 0);
        List<Pos> found = List.of(new Pos(1, 70, 0), new Pos(1, 71, 0), new Pos(1, 72, 0), new Pos(2, 72, 0), tip, tip);
        assertEquals(List.of(tip.below()), HangingLogs.under(found, Pos::below, tip.below()::equals));
        assertEquals(List.of(), HangingLogs.under(found, Pos::below, new Pos(1, 70, 0)::equals), "a found log is not taken again");
    }
}
