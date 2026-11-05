package dev.frosthaven.havencompat.settings;

import dev.frosthaven.havencompat.animations.PlayerAnimationData;
import dev.frosthaven.havencompat.portals.PortalView;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/** Haven Compat's config screen in the mod list: one row per setting whose mod is installed. */
public final class HavenSettingsScreen extends Screen {
    private static final int ROW = 24;
    private final Screen parent;

    public HavenSettingsScreen(Screen parent) {
        super(Component.translatable("haven_compat.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 125, y = height / 2 - 20;
        if (ModList.get().isLoaded("immersive_portals_core")) {
            addRenderableWidget(row(PortalView.SETTING, PortalView.Level.values(), "portal_view", x, y));
            y += ROW;
        }
        if (ModList.get().isLoaded("entity_texture_features")) {
            addRenderableWidget(row(PlayerAnimationData.SETTING, PlayerAnimationData.Level.values(), "player_animation_data", x, y));
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, height - 40, 250, 20).build());
    }

    /** A cycle button for a setting; lang keys haven_compat.settings.<key>, .<key>.<value> and .<key>.<value>.tip. */
    private static <E extends Enum<E>> CycleButton<E> row(SavedChoice<E> setting, E[] values, String key, int x, int y) {
        String base = "haven_compat.settings." + key;
        return CycleButton.<E>builder(v -> Component.translatable(base + "." + v.name().toLowerCase(Locale.ROOT)))
                .withValues(values)
                .withInitialValue(setting.get())
                .withTooltip(v -> Tooltip.create(Component.translatable(base + "." + v.name().toLowerCase(Locale.ROOT) + ".tip")))
                .create(x, y, 250, 20, Component.translatable(base), (button, chosen) -> setting.choose(chosen));
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
