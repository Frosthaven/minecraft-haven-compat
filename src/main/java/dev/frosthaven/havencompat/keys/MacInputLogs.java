package dev.frosthaven.havencompat.keys;

import dev.frosthaven.havencompat.Log;
import net.neoforged.fml.loading.LoadingModList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * MacOS Input Fixes (Neoforged port, {@code macos_input_fixes}) logs at INFO on every scroll event and on many key
 * presses (two lines per trackpad scroll step), which fills latest.log. Its loggers are set to WARN: its warnings and
 * errors still show.
 */
public final class MacInputLogs {
    static final String MOD = "macos_input_fixes";
    static final String LOGGERS = "com.hamarb123.macos_input_fixes";
    private static final Log LOG = new Log("[mac keys] ");

    private MacInputLogs() {
    }

    public static void quiet() {
        if (LoadingModList.get().getModFileById(MOD) == null) {
            return;
        }
        try {
            Configurator.setLevel(LOGGERS, Level.WARN);
            LOG.once("quiet", "MacOS Input Fixes logs WARN and above only (its INFO lines come on every scroll)");
        } catch (Throwable t) {
            LOG.error("quiet", "MacOS Input Fixes logging left as it was", t);
        }
    }
}
