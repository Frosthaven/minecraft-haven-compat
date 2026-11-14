package dev.frosthaven.havencompat.schematics;

import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import dev.frosthaven.havencompat.Log;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Deployer printed by a Schematicannon gets an owner: the player who started that print.
 * <p>Create sets a deployer's owner only in {@code DeployerBlock.setPlacedBy} (a player's hand). The cannon copies
 * "safe" data only ({@code DeployerBlockEntity.writeSafe}: the mode) and places with no placer
 * ({@code BlockHelper.placeSchematicBlock}), so a printed deployer acts as the ownerless fake player "Deployer", which
 * a claim mod that allows fake players only by their owner's id refuses (FTB Chunks: nothing placed, used or broken
 * in a claim). Create issue 6219.
 * <p>The cannon knows no player, so {@code mixin/CannonPrintStarterMixin} records the one who pressed Play or Pause
 * on it ({@code ConfigureSchematicannonPacket}; only those two take a cannon out of {@code STOPPED}) and {@code mixin/CannonDeployerOwnerMixin}
 * keeps that id in the cannon's saved data and writes it as {@code Owner} into each deployer it launches. The
 * {@code Owner} inside the schematic file is not used: a player can write any id into a file they upload.
 * <p>A deployer is any {@code DeployerBlock}: Create's own and the cased ones of Create Encased (brass, copper and
 * others; {@code CustomDeployerBlock} extends it and uses Create's {@code DeployerBlockEntity}).
 */
public final class PrintOwner {
    /** The cannon's saved data: the id of the player who last pressed Play or Pause. */
    public static final String STARTER = "HavenPrintStarter";
    private static final Log LOG = new Log("[print owner] ");

    private PrintOwner() {
    }

    /** A Schematicannon block entity (implemented by the mixin). */
    public interface Cannon {
        void haven$starter(UUID player);
    }

    /** The block entity data a cannon launches with {@code state}: a deployer's gets {@code starter} as its owner. */
    @Nullable
    public static CompoundTag launched(BlockState state, @Nullable CompoundTag data, @Nullable UUID starter) {
        if (starter == null || !(state.getBlock() instanceof DeployerBlock)) {
            return data;
        }
        CompoundTag owned = data == null ? new CompoundTag() : data;
        owned.putUUID("Owner", starter);   // read by DeployerBlockEntity.read when the block lands
        LOG.once("active", "printed deployers take the owner of the player who started the print");
        return owned;
    }
}
