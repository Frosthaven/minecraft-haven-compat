package dev.frosthaven.havencompat.binder;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.mixin.BinderSlotAccessor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Easy Mob Farm's card binder gets a search box and a remembered sort. A sort moves the real cards ({@link
 * BinderSortMessage}, also when the contents change). A search is client only: client slots move into the view's cells
 * and a click sends the real slot number, as a page change already does (the server's menu never leaves page 0). Off
 * without Easy Mob Farm; if a mixin or binder field is gone, the log says so and the binder stays as the mod made it.
 */
public final class BinderFix {
    private static final Log LOG = new Log("[card binder] ");
    private static final int PROBE_PAGE = -7;
    private static BinderSession session;
    private static boolean probing;

    private BinderFix() {}

    public static void register(IEventBus bus) {
        if (!ModList.get().isLoaded("easy_mob_farm")) return;
        bus.addListener(BinderFix::init);
        bus.addListener(BinderFix::closing);
        bus.addListener(BinderFix::tick);
        bus.addListener(BinderFix::key);
        bus.addListener(BinderFix::mouse);
        bus.addListener(BinderFix::renderPost);
    }

    /** For the slot mixin: the view's answer, or null for a slot of any other menu (the integrated server's too). */
    public static Boolean active(Object slot) {
        if (probing) return Boolean.FALSE;
        BinderSession s = session;
        return s == null ? null : s.active((Slot) slot);
    }

    /** For the menu mixin: the view's last page, or null for any other menu. */
    public static Integer lastPage(Object menu) {
        if (probing) return PROBE_PAGE;
        BinderSession s = session;
        return s == null ? null : s.lastPage(menu);
    }

    /** Asks the patched methods for a known answer: what they say proves they run, with no bookkeeping to trust. */
    private static boolean patched(AbstractContainerScreen<?> screen) throws ReflectiveOperationException {
        var menu = screen.getMenu();
        Slot card = null;
        for (Slot slot : menu.slots) {
            if (BinderTargets.SLOT_CLASS.equals(slot.getClass().getName())) {
                card = slot;
                break;
            }
        }
        if (!(card instanceof BinderSlotAccessor)) return false;
        probing = true;
        try {
            return !card.isActive() && Integer.valueOf(PROBE_PAGE).equals(menu.getClass().getMethod("getMaxPage").invoke(menu));
        } finally {
            probing = false;
        }
    }

    private static boolean isBinder(Screen screen) {
        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
            if (BinderTargets.SCREEN_CLASS.equals(c.getName())) return true;
        }
        return false;
    }

    private static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || !isBinder(screen)) return;
        try {
            if (!patched(screen)) {
                LOG.warnOnce("patches", "a binder mixin did not apply: no search or sort, the binder stays as it is");
                return;
            }
            if (session == null || !session.owns(screen)) session = new BinderSession(screen);
            session.addWidgets(event::addListener);
        } catch (ReflectiveOperationException | RuntimeException e) {
            session = null;
            LOG.error("open", "the binder classes changed: no search or sort, the binder stays as it is", e);
        }
    }

    private static void closing(ScreenEvent.Closing event) {
        if (session != null && event.getScreen() instanceof AbstractContainerScreen<?> screen && session.owns(screen)) session = null;
    }

    private static void tick(ClientTickEvent.Post event) {
        if (session != null) session.tick();
    }

    /**
     * A focused box takes every key but Escape, or E would close the binder and Q would drop a card. While a sort is on its
     * way, slot keys (Q, hotbar swaps) wait, as clicks do.
     */
    private static void key(ScreenEvent.KeyPressed.Pre event) {
        if (session == null || !session.is(event.getScreen()) || event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) return;
        if (!session.box().isFocused()) {
            if (session.busy()) event.setCanceled(true);
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_ENTER) {
            event.getScreen().setFocused(null);
        } else {
            session.box().keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        }
        event.setCanceled(true);
    }

    /** While a sort is on its way (one round trip), a click waits: the server would apply it to the moved slots. */
    private static void mouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (session == null || !session.is(event.getScreen())) return;
        if (session.busy()) {
            event.setCanceled(true);
            return;
        }
        if (session.dropdown().listClicked(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        if (session.box().isFocused() && !session.box().isMouseOver(event.getMouseX(), event.getMouseY())) event.getScreen().setFocused(null);
    }

    private static void renderPost(ScreenEvent.Render.Post event) {
        if (session != null && session.is(event.getScreen())) session.dropdown().renderList(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }
}
