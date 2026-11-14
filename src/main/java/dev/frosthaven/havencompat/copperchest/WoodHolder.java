package dev.frosthaven.havencompat.copperchest;

import java.util.Optional;
import net.minecraft.world.level.block.state.properties.WoodType;

/** Sophisticated Storage's wood storage block entity, seen through its own two methods ({@code CopperLookHolderMixin}). */
public interface WoodHolder {
    Optional<WoodType> getWoodType();

    void setWoodType(WoodType type);
}
