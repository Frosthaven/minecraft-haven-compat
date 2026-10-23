package dev.frosthaven.havencompat.schematics;

import com.simibubi.create.CreateClient;
import dev.frosthaven.havencompat.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of {@link ServerFill}: a "save to file" waits for the server's data (at most {@link #WAIT_TICKS}; then it
 * saves the client's own data, as before), then runs Create's save again with that data in place.
 */
public final class ServerFillClient {
    private static final Log LOG = new Log("[schematics] ");
    private static final int WAIT_TICKS = 100;
    private static String name;
    private static boolean upload;
    private static int waited = -1;
    private static boolean saving;

    private ServerFillClient() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            if (waited >= 0 && ++waited > WAIT_TICKS) {
                LOG.warnOnce("fill-timeout", "the server sent no schematic data in time; saved the client's own data");
                save(null);
            }
        });
    }

    /** True when the save should wait: the request went out ({@code mixin/SchematicQuillSaveMixin} cancels Create's save). */
    public static boolean ask(String fileName, boolean convertImmediately, BlockPos first, BlockPos second) {
        Minecraft mc = Minecraft.getInstance();
        if (saving || waited >= 0 || first == null || second == null || mc.getConnection() == null
                || !mc.getConnection().hasChannel(ServerFill.Request.TYPE)) {
            return false;
        }
        name = fileName;
        upload = convertImmediately;
        waited = 0;
        PacketDistributor.sendToServer(new ServerFill.Request(first, second));
        return true;
    }

    static void reply(CompoundTag blocks) {
        if (waited >= 0) {
            save(blocks);
        }
    }

    private static void save(CompoundTag blocks) {
        waited = -1;
        saving = true;
        ServerFill.filling(blocks);
        try {
            CreateClient.SCHEMATIC_AND_QUILL_HANDLER.saveSchematic(name, upload);
        } finally {
            ServerFill.filling(null);
            saving = false;
        }
    }
}
