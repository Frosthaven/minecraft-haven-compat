package dev.frosthaven.havencompat.megatorch;

import dev.frosthaven.havencompat.HavenCompat;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.FullscreenDisplayEvent;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.model.MapPolygonWithHoles;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * JourneyMap side of the Mega Torch areas: a toggle on the fullscreen map's add-on toolbar (off by default) and one
 * outline per group of overlapping areas, drawn only on the fullscreen map. The outlines are built once per list the
 * server sends ({@link #redraw}); JourneyMap keeps and draws them, so nothing runs per frame. The server is asked for
 * updates only while the map is open with the toggle on. JourneyMap finds this class by its annotation and loads it
 * only when it is installed.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class MegatorchMap implements IClientPlugin {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(HavenCompat.ID, "textures/gui/journeymap/megatorch_areas.png");
    private static final int GOLD = 0xFFCE48;
    private static final ShapeProperties STYLE = new ShapeProperties()
            .setStrokeColor(GOLD).setStrokeOpacity(1f).setStrokeWidth(2f)
            .setFillColor(GOLD).setFillOpacity(0.12f);

    private IClientAPI api;
    private final List<PolygonOverlay> overlays = new ArrayList<>();
    private boolean mapOpen;
    private boolean subscribed;

    @Override public String getModId() { return HavenCompat.ID; }

    @Override
    public void initialize(IClientAPI jm) {
        api = jm;
        MegatorchAreasClient.changed = this::redraw;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
    }

    private void addButton(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        boolean on = shown();
        event.getThemeButtonDisplay().addThemeToggleButton("haven_compat.megatorch_map.on", "haven_compat.megatorch_map.off", ICON, on,
                this::pressed);
        mapOpen = true;
        MegatorchAreasClient.watch(on);
        redraw();
    }

    private void pressed(IThemeButton button) {
        button.toggle();
        boolean on = Boolean.TRUE.equals(button.getToggled());
        MegatorchAreasClient.SHOWN.choose(on ? MegatorchAreasClient.Shown.ON : MegatorchAreasClient.Shown.OFF);
        MegatorchAreasClient.watch(on);
        redraw();
    }

    /**
     * JourneyMap lists add-on buttons in the order their mods subscribed; subscribing on the first tick, after every
     * plugin's {@code initialize}, puts this one under the others. The map closed: stop the server's updates (the
     * outlines stay, shown only on the fullscreen map).
     */
    private void tick() {
        if (!subscribed) {
            subscribed = true;
            FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(HavenCompat.ID, this::addButton);
        }
        if (mapOpen && !(Minecraft.getInstance().screen instanceof IFullscreen)) {
            mapOpen = false;
            MegatorchAreasClient.watch(false);
        }
    }

    private void redraw() {
        for (PolygonOverlay overlay : overlays) api.remove(overlay);
        overlays.clear();
        MegatorchAreas.Areas areas = MegatorchAreasClient.latest();
        if (!shown() || areas == null || areas.torches().length == 0) return;
        int r = areas.radius();
        Area union = new Area();
        int y = 64;
        for (long t : areas.torches()) {
            int x = BlockPos.getX(t);
            int z = BlockPos.getZ(t);
            y = BlockPos.getY(t);
            union.add(new Area(new Rectangle(x - r, z - r, 2 * r + 1, 2 * r + 1)));
        }
        ResourceKey<net.minecraft.world.level.Level> dimension = ResourceKey.create(Registries.DIMENSION, areas.dimension());
        int i = 0;
        for (MapPolygonWithHoles shape : PolygonHelper.createPolygonFromArea(union, y)) {
            PolygonOverlay overlay = new PolygonOverlay(HavenCompat.ID, dimension, STYLE, shape);
            overlay.setOverlayGroupName("megatorch_areas").setTitle("Mega Torch area").setActiveUIs(Context.UI.Fullscreen);
            try {
                api.show(overlay);
                overlays.add(overlay);
            } catch (Exception e) {
                MegatorchAreas.LOG.error("show", "JourneyMap refused a Mega Torch outline", e);
                return;
            }
            i++;
        }
        MegatorchAreas.LOG.once("drawn", "Mega Torch areas drawn on the map ({} outline(s) from {} torch(es))", i, areas.torches().length);
    }

    private static boolean shown() { return MegatorchAreasClient.SHOWN.get() == MegatorchAreasClient.Shown.ON; }
}
