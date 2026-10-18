package dev.frosthaven.havencompat;

import java.util.Locale;

/** The running system, for the fixes that differ by OS. {@code -Dhaven_compat.assumeMac=true} runs the macOS paths elsewhere. */
public final class Os {
    public static final String ASSUME_MAC = "haven_compat.assumeMac";

    private Os() {
    }

    public static boolean macOS() {
        return Boolean.getBoolean(ASSUME_MAC) || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
