package dev.frosthaven.havencompat.binder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The binder's order and search, pure (unit tested). A sort is a new real order for the cards, the server applies it. A
 * search only chooses which real slots show on the client: the matches, then the first empty slot, so a carried card
 * can still go in.
 */
public final class CardView {
    public static final int PER_PAGE = 25;

    /** A sort choice; the name is what the player's file stores. */
    public enum Sort {
        NAME_AZ("haven_compat.binder.sort.name_az"),
        NAME_ZA("haven_compat.binder.sort.name_za"),
        RARITY("haven_compat.binder.sort.rarity");

        public final String label;

        Sort(String label) { this.label = label; }

        /** The stored choice, or Name A-Z for a missing or unknown name. */
        public static Sort parse(String name) {
            for (Sort s : values()) {
                if (s.name().equals(name)) return s;
            }
            return NAME_AZ;
        }
    }

    /** One real slot. An empty slot has {@code empty} true; {@code rarity} is higher for a rarer card. */
    public record Card(int slot, boolean empty, String name, String searchText, int rarity) {
        public static Card none(int slot) { return new Card(slot, true, "", "", 0); }
    }

    private CardView() {}

    /** The new real order: index {@code i} takes the card now in slot {@code result[i]}. Cards first, then empty slots. */
    public static List<Integer> sorted(List<Card> cards, Sort sort) {
        List<Card> filled = new ArrayList<>();
        List<Integer> out = new ArrayList<>();
        for (Card c : cards) {
            if (!c.empty()) filled.add(c);
        }
        filled.sort(comparator(sort));
        for (Card c : filled) out.add(c.slot());
        for (Card c : cards) {
            if (c.empty()) out.add(c.slot());
        }
        return out;
    }

    /** True when the order changes nothing, so there is nothing to send. */
    public static boolean unchanged(List<Integer> order) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i) != i) return false;
        }
        return true;
    }

    /** The server's check: every slot number of {@code size} slots, each once. Anything else could lose or copy a card. */
    public static boolean rearrangesAll(List<Integer> order, int size) {
        if (order.size() != size) return false;
        boolean[] seen = new boolean[size];
        for (int slot : order) {
            if (slot < 0 || slot >= size || seen[slot]) return false;
            seen[slot] = true;
        }
        return true;
    }

    /** The real slots the view shows, in slot order. Every slot without a search. */
    public static List<Integer> shown(List<Card> cards, String query) {
        List<String> words = words(query);
        List<Integer> out = new ArrayList<>();
        Integer firstEmpty = null;
        for (Card c : cards) {
            if (words.isEmpty()) {
                out.add(c.slot());
            } else if (c.empty()) {
                if (firstEmpty == null) firstEmpty = c.slot();
            } else if (matches(c, words)) {
                out.add(c.slot());
            }
        }
        if (firstEmpty != null) out.add(firstEmpty);
        return out;
    }

    /** At least one page, so an empty result still has a page. */
    public static int pages(int shown) { return Math.max(1, (shown + PER_PAGE - 1) / PER_PAGE); }

    private static List<String> words(String query) {
        List<String> out = new ArrayList<>();
        for (String w : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    private static boolean matches(Card c, List<String> words) {
        String text = c.searchText().toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (!text.contains(w)) return false;
        }
        return true;
    }

    private static Comparator<Card> comparator(Sort sort) {
        Comparator<Card> name = Comparator.comparing((Card c) -> c.name().toLowerCase(Locale.ROOT));
        Comparator<Card> slot = Comparator.comparingInt(Card::slot);
        return switch (sort) {
            case NAME_AZ -> name.thenComparing(slot);
            case NAME_ZA -> name.reversed().thenComparing(slot);
            case RARITY -> Comparator.comparingInt(Card::rarity).reversed().thenComparing(name).thenComparing(slot);
        };
    }
}
