package dev.frosthaven.havencompat.signs;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Big Sign Writer and Unlocked Typing both put a row of widgets directly above the sign screen's Done button, at the
 * same place. With both mods installed, Unlocked Typing's row moves up one row. Vanilla types only, its widgets are
 * found by package name. Big Sign Writer's open font list covers Done and turns it inactive, but its label still shows
 * through the list, so an inactive Done is hidden.
 */
public final class SignEditorLayout {
    private static final String TYPING_PACKAGE = ".unlocked_typing.";
    private static final int ROW = 24;

    private SignEditorLayout() {}

    public static void register(IEventBus bus) {
        if (!ModList.get().isLoaded("bigsignwriter") || !ModList.get().isLoaded("unlocked_typing")) return;
        bus.addListener(SignEditorLayout::init);
        bus.addListener(SignEditorLayout::render);
    }

    private static void render(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof AbstractSignEditScreen screen)) return;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button done && done.getMessage().equals(CommonComponents.GUI_DONE)) done.visible = done.active;
        }
    }

    private static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractSignEditScreen screen)) return;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getClass().getName().contains(TYPING_PACKAGE)) {
                widget.setY(widget.getY() - ROW);
            }
        }
    }
}
