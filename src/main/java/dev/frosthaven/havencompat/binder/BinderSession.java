package dev.frosthaven.havencompat.binder;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.binder.CardView.Card;
import dev.frosthaven.havencompat.binder.CardView.Sort;
import dev.frosthaven.havencompat.mixin.BinderSlotAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.network.PacketDistributor;

/** One open binder on the client: its search text, view and widgets, and the sort it asks the server for. */
final class BinderSession {
    private static final Log LOG = new Log("[card binder] ");
    private static final int RESEND_TICKS = 40;
    private static final int MAX_TRIES = 3;
    private static final String MANAGER_CLASS = "de.markusbordihn.easymobfarm.capture.MobCaptureManager";
    private static final int GRID_X = 22, GRID_Y = 24, CELL = 20, COLUMNS = 5;
    private static final int SEARCH_X = 4, SORT_X = 142, BAR_WIDTH = 134, BAR_HEIGHT = 15, BAR_ABOVE = 17;

    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;
    private final int cardSlots;
    private final int[] position;
    private final List<ItemStack> seen = new ArrayList<>();
    private final Method setMenuPage;
    private final Field screenPage;
    private final Field selectedSlot;
    private final Method updateButtons;
    private final Method captureData;
    private String query = "";
    private List<Integer> sent;
    private int ticks;
    private int sentAt;
    private int sentState;
    private boolean pending;
    private int tries;
    private List<Integer> order = List.of();
    private EditBox box;
    private SortDropdown dropdown;

    /** Throws when a binder class no longer has what the view needs: the caller then leaves the binder as it is. */
    BinderSession(AbstractContainerScreen<?> screen) throws ReflectiveOperationException {
        this.screen = screen;
        this.menu = screen.getMenu();
        int count = 0;
        for (Slot slot : menu.slots) {
            if (BinderTargets.SLOT_CLASS.equals(slot.getClass().getName())) count++;
        }
        cardSlots = count;
        position = new int[count];
        setMenuPage = menu.getClass().getMethod("setCurrentPage", int.class);
        Class<?> binder = Class.forName(BinderTargets.SCREEN_CLASS, false, screen.getClass().getClassLoader());
        screenPage = binder.getDeclaredField("currentPage");
        screenPage.setAccessible(true);
        selectedSlot = binder.getDeclaredField("selectedSlotIndex");
        selectedSlot.setAccessible(true);
        updateButtons = binder.getDeclaredMethod("updatePageButtons");
        updateButtons.setAccessible(true);
        captureData = Class.forName(MANAGER_CLASS, false, screen.getClass().getClassLoader()).getMethod("getMobCaptureData", ItemStack.class);
        rebuild();
    }

    boolean owns(AbstractContainerScreen<?> other) { return other == screen; }

    boolean is(Screen other) { return other == screen; }

    /** The screen ran init (first open or a resize): new widgets with the same state. */
    void addWidgets(java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> add) {
        Minecraft mc = Minecraft.getInstance();
        // Above the book: its pages draw the title, the card name and the mob preview in all the free space.
        int x = screen.getGuiLeft(), y = Math.max(1, screen.getGuiTop() - BAR_ABOVE);
        box = new EditBox(mc.font, x + SEARCH_X, y, BAR_WIDTH, BAR_HEIGHT, Component.translatable("haven_compat.binder.search"));
        box.setHint(Component.translatable("haven_compat.binder.search"));
        box.setMaxLength(64);
        box.setValue(query);
        box.setResponder(this::searched);
        dropdown = new SortDropdown(x + SORT_X, y, BAR_WIDTH, BAR_HEIGHT, BinderSort.get(), this::sorted);
        add.accept(box);
        add.accept(dropdown);
    }

    EditBox box() { return box; }

    SortDropdown dropdown() { return dropdown; }

    Boolean active(Slot slot) {
        if (slot.index >= cardSlots || menu.slots.get(slot.index) != slot) return null;
        int at = position[slot.index];
        return at >= 0 && at / CardView.PER_PAGE == currentPage();
    }

    Integer lastPage(Object other) {
        return other == menu ? CardView.pages(order.size()) - 1 : null;
    }

    private void searched(String text) {
        query = text;
        rebuild();
        page(0);
    }

    private void sorted(Sort sort) {
        BinderSort.set(sort);
        rebuild();
        page(0);
    }

    /** True from a sort request until its reply: a card click now would land on a slot the sort moves. */
    boolean busy() { return pending; }

    /**
     * Asks the server for the sorted order, for the menu state seen now. One request at a time: the next waits for a
     * reply (the menu state changes) or {@link #RESEND_TICKS}. The same order goes at most {@link #MAX_TRIES} times.
     */
    private void askForSort(List<Card> cards) {
        List<Integer> wanted = CardView.sorted(cards, BinderSort.get());
        if (CardView.unchanged(wanted)) {
            sent = null;
            pending = false;
            tries = 0;
            return;
        }
        if (pending) return;
        if (!wanted.equals(sent)) {
            tries = 0;
        } else if (tries >= MAX_TRIES) {
            LOG.warnOnce("stuck", "the server did not apply the binder sort after " + MAX_TRIES + " tries: the cards stay in their order");
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(BinderSortMessage.TYPE)) {
            LOG.warnOnce("server", "the server has no haven-compat with the binder sort: the cards stay in their order");
            return;
        }
        sent = wanted;
        sentAt = ticks;
        sentState = menu.getStateId();
        pending = true;
        tries++;
        PacketDistributor.sendToServer(new BinderSortMessage(menu.containerId, sentState, wanted));
    }

    /** Each client tick: a reply, or a card that came or went, updates the view at once. */
    void tick() {
        ticks++;
        if (pending && (menu.getStateId() != sentState || ticks - sentAt >= RESEND_TICKS)) {
            pending = false;
            rebuild();
            page(currentPage());
            return;
        }
        for (int i = 0; i < cardSlots; i++) {
            if (!ItemStack.matches(seen.get(i), menu.slots.get(i).getItem())) {
                rebuild();
                page(currentPage());
                return;
            }
        }
    }

    private void rebuild() {
        seen.clear();
        List<Card> cards = new ArrayList<>(cardSlots);
        for (int i = 0; i < cardSlots; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            seen.add(stack.copy());
            cards.add(stack.isEmpty() ? Card.none(i) : card(i, stack));
        }
        askForSort(cards);
        order = CardView.shown(cards, query);
        java.util.Arrays.fill(position, -1);
        for (int at = 0; at < order.size(); at++) {
            int slot = order.get(at);
            position[slot] = at;
            int cell = at % CardView.PER_PAGE;
            BinderSlotAccessor move = (BinderSlotAccessor) menu.slots.get(slot);
            move.haven_compat$setX(GRID_X + cell % COLUMNS * CELL);
            move.haven_compat$setY(GRID_Y + cell / COLUMNS * CELL);
        }
    }

    private Card card(int slot, ItemStack stack) {
        String name = stack.getHoverName().getString();
        StringBuilder text = new StringBuilder(name);
        int rarity = 0;
        try {
            Object data = captureData.invoke(null, stack);
            if (data.getClass().getMethod("entityType").invoke(data) instanceof EntityType<?> type) {
                text.append(' ').append(BuiltInRegistries.ENTITY_TYPE.getKey(type));
            }
            if (data.getClass().getMethod("rarity").invoke(data) instanceof Rarity value) rarity = value.ordinal();
        } catch (ReflectiveOperationException e) {
            LOG.error("card data", "could not read a card's mob and rarity: search uses its name, rarity sort has no effect", e);
        }
        return new Card(slot, false, name, text.toString(), rarity);
    }

    int currentPage() {
        try {
            return screenPage.getInt(screen);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    private void page(int wanted) {
        int page = Math.max(0, Math.min(wanted, CardView.pages(order.size()) - 1));
        try {
            setMenuPage.invoke(menu, page);
            screenPage.setInt(screen, page);
            selectedSlot.setInt(screen, -1);
            if (dropdown != null) updateButtons.invoke(screen);
        } catch (ReflectiveOperationException e) {
            LOG.error("page", "could not set the binder page", e);
        }
    }
}
