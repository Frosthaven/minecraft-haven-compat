package dev.frosthaven.havencompat;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * This mod's log (logger "haven_compat" in latest.log). Nothing logs per frame: {@link #once} logs a key the first time
 * only, so a session's log reads as the list of what each fix found and did.
 */
public final class Log {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private final String prefix;

    /** A channel per fix, e.g. {@code new Log("[portal depth] ")}. */
    public Log(String prefix) { this.prefix = prefix; }

    /** Logs msg (info) the first time key is seen on this channel. */
    public void once(String key, String msg, Object... args) {
        if (SEEN.add(prefix + "once:" + key)) LOGGER.info(prefix + msg, args);
    }

    /** Logs msg (warn) the first time key is seen on this channel. */
    public void warnOnce(String key, String msg, Object... args) {
        if (SEEN.add(prefix + "warn:" + key)) LOGGER.warn(prefix + msg, args);
    }

    /** Logs an error with its stack trace the first time key is seen on this channel. */
    public void error(String key, String msg, Throwable t) {
        if (SEEN.add(prefix + "err:" + key)) LOGGER.error(prefix + msg, t);
    }
}
