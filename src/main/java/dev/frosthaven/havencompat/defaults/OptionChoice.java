package dev.frosthaven.havencompat.defaults;

/**
 * What to do with one shader pack option: {@code have} is the value in the pack's option file (null: unset),
 * {@code before} the value this mod wrote there before (null: never), {@code want} the pack's value for this OS.
 * The pack sets a default once and moves it when the pack changes it; a value the player set stays.
 */
public enum OptionChoice {
    /** The file already holds the pack's value: record it, write nothing. */
    ALREADY,
    /** Write the pack's value: the key is unset, or still holds what this mod wrote before. */
    WRITE,
    /** The player set another value: leave it. */
    PLAYERS;

    public static OptionChoice decide(String have, String before, String want) {
        if (want.equals(have)) {
            return ALREADY;
        }
        if (have != null && !have.equals(before)) {
            return PLAYERS;
        }
        return WRITE;
    }
}
