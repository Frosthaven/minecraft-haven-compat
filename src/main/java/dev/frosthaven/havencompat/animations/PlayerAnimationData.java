package dev.frosthaven.havencompat.animations;

import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.settings.SavedChoice;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Player Animation Data setting (Experimental Optimizations). Entity Texture Features gives resource pack rules
 * ({@code nbt(...)} in Entity Model Features animations, {@code nbt.} in random texture properties) an entity's full
 * data, as {@code /data get entity} shows it. For a player, that is the whole inventory: Fresh Animations' FA+Player
 * turns it into text and searches it, several times per frame and again in the shader shadow pass. On a full modded
 * inventory this took about half of the render thread.
 *
 * <p>Optimized (default): players get a small copy with the fields that player animations read: the off-hand slot in
 * {@code Inventory} (slot -106), {@code SelectedItem}, {@code SelectedItemSlot}, {@code abilities} and the sleeping
 * position. The copy is built once per player per tick. Full: the full data, as without this mod. Other entities always
 * get the full data.
 */
public final class PlayerAnimationData {
    public enum Level { OPTIMIZED, FULL }

    private static final Log LOG = new Log("[player animation data] ");
    public static final SavedChoice<Level> SETTING = new SavedChoice<>(Level.class, Level.OPTIMIZED,
            "haven-compat-player-animation-data.json", "playerAnimationData", "Player Animation Data", LOG);

    private static final byte OFFHAND_SLOT = (byte) 150;   // Inventory.save: off hand at 150, as a byte -106
    private static final Map<Integer, CompoundTag> COPIES = new HashMap<>();
    private static long copiesTick = Long.MIN_VALUE;
    private static MethodHandle entityOfState;
    private static boolean broken;

    private PlayerAnimationData() {}

    /**
     * The small copy for the entity of an ETF render state, or null when ETF must use its full data: the setting is Full,
     * the entity is not a player, or the state could not be read. Render thread only.
     */
    public static CompoundTag copyFor(Object state) {
        if (SETTING.get() != Level.OPTIMIZED || broken) return null;
        Object entity;
        try {
            if (entityOfState == null) {
                Class<?> type = Class.forName("traben.entity_texture_features.features.state.ETFEntityRenderState");
                entityOfState = MethodHandles.publicLookup().findVirtual(type, "entity", MethodType.methodType(
                        Class.forName("traben.entity_texture_features.utils.ETFEntity")));
            }
            entity = entityOfState.invoke(state);
        } catch (Throwable t) {
            broken = true;
            LOG.error("state", "could not read the entity of an ETF render state, players get their full data", t);
            return null;
        }
        if (!(entity instanceof Player player)) return null;
        long tick = player.level().getGameTime();
        if (tick != copiesTick) {
            COPIES.clear();
            copiesTick = tick;
        }
        return COPIES.computeIfAbsent(player.getId(), id -> build(player));
    }

    private static CompoundTag build(Player player) {
        LOG.once("first", "players get the small animation data copy (setting Optimized)");
        var registries = player.registryAccess();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:player");
        ItemStack offhand = player.getOffhandItem();
        ListTag inventory = new ListTag();
        if (!offhand.isEmpty()) {
            CompoundTag slot = new CompoundTag();
            slot.putByte("Slot", OFFHAND_SLOT);
            inventory.add(offhand.save(registries, slot));
        }
        tag.put("Inventory", inventory);
        ItemStack selected = player.getInventory().getSelected();
        if (!selected.isEmpty()) tag.put("SelectedItem", selected.save(registries));
        tag.putInt("SelectedItemSlot", player.getInventory().selected);
        player.getAbilities().addSaveData(tag);
        player.getSleepingPos().ifPresent(pos -> {
            tag.putInt("SleepingX", pos.getX());
            tag.putInt("SleepingY", pos.getY());
            tag.putInt("SleepingZ", pos.getZ());
        });
        return tag;
    }
}
