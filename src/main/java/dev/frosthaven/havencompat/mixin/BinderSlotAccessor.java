package dev.frosthaven.havencompat.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Moves a client card slot to its cell in the binder's view. The plugin applies this only with Easy Mob Farm present. */
@Mixin(Slot.class)
public interface BinderSlotAccessor {
    @Mutable @Accessor("x") void haven_compat$setX(int x);

    @Mutable @Accessor("y") void haven_compat$setY(int y);
}
