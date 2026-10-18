package dev.frosthaven.havencompat.servers;

import dev.frosthaven.havencompat.servers.ServerMatch.Change;
import dev.frosthaven.havencompat.servers.ServerMatch.Entry;
import dev.frosthaven.havencompat.servers.ServerMatch.Kind;
import dev.frosthaven.havencompat.servers.ServerMatch.PackServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMatchTest {
    private static final PackServer HAVEN = new PackServer("Haven", "minecraft.thedragon.dev:9205",
            List.of("minecraft.thedragon.dev"));

    @Test
    void bareNameFromTheOldDefaultGetsThePort() {
        List<Change> plan = ServerMatch.plan(List.of(new Entry("Haven", "minecraft.thedragon.dev")), List.of(HAVEN));
        assertEquals(List.of(new Change(Kind.SET, 0, "Haven", "minecraft.thedragon.dev:9205")), plan);
    }

    @Test
    void aPreviousAddressMovesToTheNewHostEvenWhenRenamed() {
        PackServer moved = new PackServer("Haven", "play.example.org", List.of("minecraft.thedragon.dev:9205", "minecraft.thedragon.dev"));
        List<Change> plan = ServerMatch.plan(List.of(new Entry("Shane's server", "Minecraft.TheDragon.dev:9205 ")), List.of(moved));
        assertEquals(List.of(new Change(Kind.SET, 0, "Shane's server", "play.example.org")), plan);
    }

    @Test
    void thePackNameMatchesWhateverAddressItHas() {
        List<Change> plan = ServerMatch.plan(List.of(new Entry(" haven ", "old.example.net")), List.of(HAVEN));
        assertEquals(Kind.SET, plan.get(0).kind());
        assertEquals(1, plan.size());
    }

    @Test
    void otherServersAndACurrentEntryAreLeftAlone() {
        List<Entry> have = List.of(new Entry("Hypixel", "mc.hypixel.net"), new Entry("Haven", "minecraft.thedragon.dev:9205"),
                new Entry("Friend", "minecraft.thedragon.dev:9206"));
        assertTrue(ServerMatch.plan(have, List.of(HAVEN)).isEmpty());
    }

    @Test
    void missingServerIsAdded() {
        List<Change> plan = ServerMatch.plan(List.of(new Entry("Hypixel", "mc.hypixel.net")), List.of(HAVEN));
        assertEquals(List.of(new Change(Kind.ADD, -1, "Haven", "minecraft.thedragon.dev:9205")), plan);
    }

    @Test
    void anEntryThatBecomesADuplicateGoesAfterTheSets() {
        List<Entry> have = List.of(new Entry("Haven", "minecraft.thedragon.dev"), new Entry("Haven", "minecraft.thedragon.dev:9205"));
        List<Change> plan = ServerMatch.plan(have, List.of(HAVEN));
        assertEquals(List.of(new Change(Kind.SET, 0, "Haven", "minecraft.thedragon.dev:9205"),
                new Change(Kind.REMOVE, 1, "Haven", "minecraft.thedragon.dev:9205")), plan);
    }

    @Test
    void theDefaultPortIsTheSameAsNone() {
        assertTrue(ServerMatch.sameAddress("Host.example:25565", "host.example"));
        assertTrue(ServerMatch.plan(List.of(new Entry("Haven", "minecraft.thedragon.dev:9205")), List.of(HAVEN)).isEmpty());
    }
}
