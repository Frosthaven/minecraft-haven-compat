package dev.frosthaven.havencompat.keys;

import com.mojang.blaze3d.platform.InputConstants;
import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * macOS only: Control + the drop key (Q) drops the whole stack, as on Windows and Linux. On macOS Minecraft's "Control"
 * ({@code Screen.hasControlDown()}) is Cmd, so the whole-stack drop was Cmd+Q, which also quits the game. Cmd+Q still
 * drops the stack (and quits); the real Control keys now count too, for the drop only. The mixins
 * ({@code MacDropStackMixin}, {@code MacDropStackScreenMixin}) apply only on macOS ({@code CompatMixinPlugin}).
 */
public final class MacDropStack {
    private static final Log LOG = new Log("[mac keys] ");

    private MacDropStack() {
    }

    /** The whole-stack decision of a drop: vanilla's (Cmd on macOS), or a Control key held. */
    public static boolean wholeStack(boolean vanilla) {
        if (vanilla) {
            return true;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean control = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (control) {
            LOG.once("control", "Control + drop: whole stack dropped (macOS)");
        }
        return control;
    }
}
