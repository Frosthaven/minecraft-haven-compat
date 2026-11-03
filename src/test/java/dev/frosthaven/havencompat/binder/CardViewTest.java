package dev.frosthaven.havencompat.binder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frosthaven.havencompat.binder.CardView.Card;
import dev.frosthaven.havencompat.binder.CardView.Sort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CardViewTest {
    private static Card card(int slot, String name, int rarity) { return new Card(slot, false, name, name + " minecraft:" + name.toLowerCase(), rarity); }

    private static final List<Card> CARDS = List.of(card(0, "Zombie", 0), Card.none(1), card(2, "Axolotl", 2), card(3, "Zombie Villager", 1), Card.none(4));

    @Test void aSortPutsCardsFirstAndCloseGaps() {
        assertEquals(List.of(2, 0, 3, 1, 4), CardView.sorted(CARDS, Sort.NAME_AZ));
        assertEquals(List.of(3, 0, 2, 1, 4), CardView.sorted(CARDS, Sort.NAME_ZA));
        assertEquals(List.of(2, 3, 0, 1, 4), CardView.sorted(CARDS, Sort.RARITY));
    }

    @Test void aSortedBinderHasNothingToSend() {
        List<Card> done = List.of(card(0, "Axolotl", 2), card(1, "Pig", 0), card(2, "Pig", 0), Card.none(3));
        assertTrue(CardView.unchanged(CardView.sorted(done, Sort.NAME_AZ)));
        assertFalse(CardView.unchanged(CardView.sorted(done, Sort.NAME_ZA)));
    }

    @Test void theServerAcceptsOnlyAFullRearrangement() {
        assertTrue(CardView.rearrangesAll(List.of(2, 0, 1), 3));
        assertFalse(CardView.rearrangesAll(List.of(2, 0), 3));
        assertFalse(CardView.rearrangesAll(List.of(2, 0, 0), 3));
        assertFalse(CardView.rearrangesAll(List.of(2, 0, 3), 3));
        assertFalse(CardView.rearrangesAll(List.of(-1, 0, 1), 3));
    }

    @Test void noSearchShowsEverySlot() {
        assertEquals(List.of(0, 1, 2, 3, 4), CardView.shown(CARDS, "  "));
    }

    @Test void aSearchShowsMatchesThenOneEmptySlot() {
        assertEquals(List.of(0, 3, 1), CardView.shown(CARDS, "ZOM"));
        assertEquals(List.of(2, 1), CardView.shown(CARDS, "minecraft:axo"));
        assertEquals(List.of(3, 1), CardView.shown(CARDS, " villager  zombie "));
        assertEquals(List.of(1), CardView.shown(CARDS, "zombie axolotl"));
    }

    @Test void aFullBinderHasNoEmptySlotToAdd() {
        List<Card> full = new ArrayList<>();
        for (int i = 0; i < 250; i++) full.add(card(i, i == 200 ? "Warden" : "Pig " + i, 0));
        assertEquals(List.of(200), CardView.shown(full, "warden"));
        assertEquals(10, CardView.pages(CardView.shown(full, "").size()));
    }

    @Test void pageCountHasAtLeastOnePage() {
        assertEquals(1, CardView.pages(0));
        assertEquals(1, CardView.pages(25));
        assertEquals(2, CardView.pages(26));
    }

    @Test void anUnknownStoredSortFallsBackToNameAz() {
        assertEquals(Sort.RARITY, Sort.parse("RARITY"));
        assertEquals(Sort.NAME_AZ, Sort.parse("BINDER"));
        assertEquals(Sort.NAME_AZ, Sort.parse(null));
    }
}
