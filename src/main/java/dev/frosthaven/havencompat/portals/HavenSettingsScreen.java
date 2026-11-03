package dev.frosthaven.havencompat.portals;

import dev.frosthaven.havencompat.portals.PortalView.Level;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Haven Compat's config screen in the mod list. */
public final class HavenSettingsScreen extends Screen {
    private final Screen parent;

    public HavenSettingsScreen(Screen parent) {
        super(Component.translatable("haven_compat.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 125, y = height / 2 - 20;
        addRenderableWidget(CycleButton.<Level>builder(l -> Component.translatable("haven_compat.settings.portal_view." + l.name().toLowerCase(Locale.ROOT)))
                .withValues(Level.values())
                .withInitialValue(PortalView.level())
                .withTooltip(l -> Tooltip.create(Component.translatable("haven_compat.settings.portal_view." + l.name().toLowerCase(Locale.ROOT) + ".tip")))
                .create(x, y, 250, 20, Component.translatable("haven_compat.settings.portal_view"), (button, chosen) -> PortalView.choose(chosen)));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, height - 40, 250, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("haven_compat.settings.experimental"), width / 2, height / 2 - 38, 0xFFFF55);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
