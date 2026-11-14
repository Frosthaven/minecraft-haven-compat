package dev.frosthaven.havencompat.spells;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CasterItemsTest {
    interface Scribeable { }
    interface CasterTool extends Scribeable { }
    static class VanillaItem { public Object use(Object level, Object player, Object hand) { return null; } }
    static class VanillaSword extends VanillaItem { }
    static class ModItem extends VanillaItem { }
    static class Book extends ModItem implements CasterTool { @Override public Object use(Object level, Object player, Object hand) { return null; } }
    static class AddonBook extends Book { }
    static class Sword extends VanillaSword implements CasterTool { }
    static class Parchment extends ModItem implements CasterTool { }
    static class DominionWand extends ModItem { @Override public Object use(Object level, Object player, Object hand) { return null; } }

    private static boolean casts(Class<?> item) {
        return CasterItems.castsOnUse(item, CasterTool.class.getName(), CasterItemsTest.class.getName() + "$Vanilla");
    }

    @Test
    void casterWithItsOwnUseCasts() {
        assertTrue(casts(Book.class));
        assertTrue(casts(AddonBook.class), "a subclass inherits the interface and the use");
    }

    @Test
    void casterThatKeepsVanillaUseDoesNot() {
        assertFalse(casts(Sword.class));
        assertFalse(casts(Parchment.class));
    }

    @Test
    void otherItemsDoNot() {
        assertFalse(casts(DominionWand.class));
        assertFalse(casts(VanillaItem.class));
    }
}
