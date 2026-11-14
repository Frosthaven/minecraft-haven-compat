package dev.frosthaven.havencompat.schematics;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import dev.frosthaven.havencompat.Log;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
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
 * <p>Every other printed block gets the same owner the way a hand placement gives it. Create already calls
 * {@code Block.setPlacedBy} for each block it lands ({@code BlockHelper.placeSchematicBlock}, after the block entity
 * data is loaded), with no placer; while a cannon lands its blocks ({@link #landing}) that placer is a fake player
 * with the starter's id and name ({@code mixin/SchematicPlacerMixin}), so blocks that take an owner there get one
 * (Mekanism security, AE2 and Refined Storage node owners, SecurityCraft, mailboxes and others). Never the real
 * player, also when online: some blocks read the placer's hands (copycats and drawers take the off-hand item), open
 * a screen for them or turn towards them. {@link #NOT_PLACED}: mods whose blocks stay with no placer.
 * <p>A deployer is any {@code DeployerBlock}: Create's own and the cased ones of Create Encased (brass, copper and
 * others; {@code CustomDeployerBlock} extends it and uses Create's {@code DeployerBlockEntity}).
 */
public final class PrintOwner {
    /** The cannon's saved data: the id of the player who last pressed Play or Pause. */
    public static final String STARTER = "HavenPrintStarter";
    private static final Log LOG = new Log("[print owner] ");

    /**
     * Block namespaces that keep Create's own call with no placer: Waystones (activates the new waystone for the placer
     * and opens its screen), MineColonies (colony blocks, graves turn towards the placer), Create Furnitures and Create
     * Stuff 'N Additions (MCreator blocks that act on the placer's position).
     */
    private static final Set<String> NOT_PLACED = Set.of("waystones", "minecolonies", "create_furnitures", "create_sa");
    /** The starter of the cannon that lands its blocks right now on this thread, or null. */
    private static final ThreadLocal<UUID> LANDING = new ThreadLocal<>();

    private PrintOwner() {
    }

    /** A cannon starts ({@code starter}, may be null) or ends (null) landing its flying blocks. */
    public static void landing(@Nullable UUID starter) {
        if (starter == null) {
            LANDING.remove();
        } else {
            LANDING.set(starter);
        }
    }

    /**
     * The placer for {@code setPlacedBy} of a block Create lands from a schematic: a fake player with the id and name
     * of the print's starter while a cannon lands blocks, else {@code placer} as Create passed it (null). The name
     * comes from the online player or the server's profile cache; a starter the server no longer knows gives no placer.
     */
    @Nullable
    public static LivingEntity placer(Level level, BlockState state, @Nullable LivingEntity placer) {
        UUID starter = LANDING.get();
        if (starter == null || !(level instanceof ServerLevel server)
                || NOT_PLACED.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
            return placer;
        }
        try {
            ServerPlayer online = server.getServer().getPlayerList().getPlayer(starter);
            String name = online != null ? online.getGameProfile().getName()
                    : server.getServer().getProfileCache() == null ? null
                    : server.getServer().getProfileCache().get(starter).map(GameProfile::getName).orElse(null);
            if (name == null) {
                LOG.warnOnce("unknown " + starter, "print starter {} is not in the server's profile cache: its printed blocks get no placer", starter);
                return placer;
            }
            LOG.once("placer", "printed blocks are placed for the player who started the print (a fake player with their id)");
            return FakePlayerFactory.get(server, new GameProfile(starter, name));
        } catch (RuntimeException e) {
            LOG.error("placer", "no placer for printed blocks; they land as Create places them", e);
            return placer;
        }
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
