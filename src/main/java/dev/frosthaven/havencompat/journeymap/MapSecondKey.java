package dev.frosthaven.havencompat.journeymap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.frosthaven.havencompat.Log;
import java.lang.reflect.Method;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * A second key for JourneyMap's fullscreen map (default M; JourneyMap's own {@code key.journeymap.map_toggle_alt} is J,
 * and it has only one). It does what JourneyMap's key does (KeybindingHandler 6.0.10): in play,
 * {@code UIManager.getOrOpenFullscreenMap()}; on the fullscreen map with no chat open and the search box not focused,
 * {@code UIManager.closeAll()}. JourneyMap is reached by reflection; the key exists only with JourneyMap loaded.
 */
public final class MapSecondKey {
    private static final Log LOG = new Log("[map key] ");
    private static final String FULLSCREEN = "journeymap.client.ui.fullscreen.Fullscreen";
    static final KeyMapping KEY = new KeyMapping("key.haven_compat.open_map", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "jm.common.hotkeys_keybinding_category");

    private static Object ui;            // UIManager.INSTANCE
    private static Method open, close;   // getOrOpenFullscreenMap, closeAll
    private static boolean broken;

    private MapSecondKey() {}

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(KEY));
        gameBus.addListener(ClientTickEvent.Post.class, e -> tick());
        gameBus.addListener(ScreenEvent.KeyPressed.Pre.class, MapSecondKey::onScreenKey);
    }

    private static void tick() {
        while (KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && mc.player != null) call(true);
        }
    }

    /** On the fullscreen map the key closes it, as JourneyMap's own key does there (its screen action). */
    private static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!KEY.matches(event.getKeyCode(), event.getScanCode()) || !FULLSCREEN.equals(screen.getClass().getName())) return;
        if (typing(screen)) return;
        if (call(false)) event.setCanceled(true);
    }

    /** JourneyMap's {@code inFullscreenWithoutChat()}: chat open or the search box focused means the key is a letter. */
    static boolean typing(Screen screen) {
        try {
            Class<?> c = screen.getClass();
            return (boolean) c.getMethod("isChatOpen").invoke(screen) || (boolean) c.getMethod("isSearchFocused").invoke(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("typing", "cannot read JourneyMap's chat and search state; the second key does not close the map", e);
            return true;
        }
    }

    /** Opens (true) or closes (false) the fullscreen map; false when JourneyMap could not be reached. */
    static boolean call(boolean opening) {
        if (broken) return false;
        try {
            if (ui == null) {
                Class<?> c = Class.forName("journeymap.client.ui.UIManager");
                ui = c.getField("INSTANCE").get(null);
                open = c.getMethod("getOrOpenFullscreenMap");
                close = c.getMethod("closeAll");
                LOG.once("ready", "{} opens and closes JourneyMap's fullscreen map", KEY.getTranslatedKeyMessage().getString());
            }
            (opening ? open : close).invoke(ui);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            LOG.error("call", "cannot reach JourneyMap's UIManager; the second map key does nothing", e);
            return false;
        }
    }
}
