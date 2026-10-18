package dev.frosthaven.havencompat.servers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.frosthaven.havencompat.Log;
import dev.frosthaven.havencompat.servers.ServerMatch.Change;
import dev.frosthaven.havencompat.servers.ServerMatch.Entry;
import dev.frosthaven.havencompat.servers.ServerMatch.PackServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the pack's server entries current in the player's server list. Default Options copies the pack's
 * {@code servers.dat} only into an instance that has none yet, so a changed address (a port spelled out, a new host
 * name) never reached a player who already had the list. On the first client tick of a session (after every mod's
 * setup, before any screen the player can reach; no title screen needed, so a quick-play launch gets it too) this reads
 * the pack's
 * {@code config/haven-pack-defaults.json}:
 * <pre>
 * {"servers": [{"name": "Haven", "address": "minecraft.thedragon.dev:9205", "previous": ["minecraft.thedragon.dev"]}]}
 * </pre>
 * and applies {@link ServerMatch#plan}: every entry that is that server (its address is the current or a previous
 * one, or it carries the pack's entry name) gets the current address, a server with no entry is added at the top,
 * and an entry that became a duplicate goes. Other entries, names and the order stay the player's. No file or no
 * {@code servers} key: nothing happens. Nothing here can stop the game: an error is logged and the list is left as it was.
 */
public final class ServerListSync {
    static final String FILE = "haven-pack-defaults.json";
    private static final Log LOG = new Log("[servers] ");
    private static boolean done;

    private ServerListSync() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(ClientTickEvent.Post.class, ServerListSync::onTick);
    }

    static void onTick(ClientTickEvent.Post event) {
        if (done) {
            return;
        }
        done = true;
        try {
            apply(FMLPaths.CONFIGDIR.get().resolve(FILE));
        } catch (Throwable t) {
            LOG.error("apply", "server list left as it was", t);
        }
    }

    static void apply(Path file) throws IOException {
        List<PackServer> want = read(file);
        if (want.isEmpty()) {
            LOG.once("none", "no servers in {}: nothing to keep current", file.getFileName());
            return;
        }
        ServerList list = new ServerList(Minecraft.getInstance());
        list.load();
        List<Entry> have = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            have.add(new Entry(list.get(i).name, list.get(i).ip));
        }
        List<Change> changes = ServerMatch.plan(have, want);
        if (changes.isEmpty()) {
            LOG.once("current", "server list current ({} pack server(s), {} entries)", want.size(), have.size());
            return;
        }
        for (Change change : changes) {
            switch (change.kind()) {
                case SET -> {
                    ServerData entry = list.get(change.index());
                    LOG.once("set:" + change.index(), "\"{}\": {} -> {}", entry.name, entry.ip, change.address());
                    entry.ip = change.address();
                }
                case REMOVE -> {
                    LOG.once("remove:" + change.index(), "\"{}\" ({}) removed: a duplicate", change.name(), change.address());
                    list.remove(list.get(change.index()));
                }
                case ADD -> {
                    list.add(new ServerData(change.name(), change.address(), ServerData.Type.OTHER), false);
                    for (int i = list.size() - 1; i > 0; i--) {   // to the top
                        list.swap(i, i - 1);
                    }
                    LOG.once("add:" + change.name(), "\"{}\" ({}) added to the server list", change.name(), change.address());
                }
            }
        }
        list.save();
    }

    /** The {@code servers} of the pack defaults file; empty when the file or the key is absent. */
    static List<PackServer> read(Path file) throws IOException {
        List<PackServer> servers = new ArrayList<>();
        if (!Files.exists(file)) {
            return servers;
        }
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!root.has("servers")) {
            return servers;
        }
        for (JsonElement element : root.getAsJsonArray("servers")) {
            JsonObject o = element.getAsJsonObject();
            List<String> previous = new ArrayList<>();
            JsonArray old = o.has("previous") ? o.getAsJsonArray("previous") : new JsonArray();
            for (JsonElement p : old) {
                previous.add(p.getAsString());
            }
            servers.add(new PackServer(o.get("name").getAsString(), o.get("address").getAsString(), previous));
        }
        return servers;
    }
}
