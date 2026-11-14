package dev.frosthaven.havencompat.mixin;

import dev.frosthaven.havencompat.copperchest.WoodHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Sophisticated Storage's wood storage block entity already has {@link WoodHolder}'s two methods; this names them. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedstorage.block.WoodStorageBlockEntity", remap = false)
public abstract class CopperLookHolderMixin implements WoodHolder {
}
