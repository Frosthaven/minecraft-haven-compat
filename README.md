# Haven Compat

Fixes between mods of the Haven server's modpack (Minecraft 1.21.1, NeoForge) that belong to none of the
mods themselves. Each fix switches itself off when the mods it is about are not installed, so the jar is safe in any
pack. Nothing to configure. All fixes run in the game client except the Mekanism, schematic and portal crossing ones, which run on the server.

| Fix | Mods | What it does |
|---|---|---|
| Portals with shaders on macOS | Immersive Portals (Aeronautics fork), Iris | Portals render through with shaders on, on a computer whose OpenGL has no `glCopyImageSubData` (OpenGL 4.3; macOS stops at 4.1). Without it the game aborts near a portal the moment shaders go on. |
| First look through a portal | Immersive Portals, Iris | The other dimensions' shader pipelines are built while the player joins, not at the first look through a portal (which cost about a second of shader linking and a full chunk rebuild: several seconds of jitter). |
| Server list stays current | the pack's `config/haven-pack-defaults.json` | The pack's server entries in the player's server list get the pack's current address: a port spelled out, a new host name. Default Options copies the pack's list on a first launch only, so an address change never reached existing players. |
| No update popups from mods | Create Deep Seas | The mod's own "Deep Seas Update" screen, shown in front of the title screen whenever Modrinth has a newer build (alphas included), is off, and so is its request to Modrinth at start. The pack manages mod versions. |
| Mekanism blocks update again (server) | Mekanism, Immersive Portals | Mekanism's block updates (a fluid tank's level, a machine's state) reach the players again. Immersive Portals keeps its own chunk tracking, Mekanism asked the empty vanilla one, so it sent nothing: tanks looked empty although they held fluid. |
| Chemicals visible in tanks with shaders | Mekanism, Iris | With a shader pack on, the chemical inside a Dynamic Tank (and an Industrial Turbine, Boiler or Fission Reactor) is lit like a light source, so it shows in a dark room as it does without shaders. Mekanism draws multiblock contents at full sky light, which vanilla shows as full brightness anywhere; Complementary lights block entities from the real scene, so a pale gas (its alpha follows the fill level) in a roofed room was drawn but not visible. Fluids and the no-shaders look are unchanged. |
| No outdated resource pack warnings | vanilla | Resource packs made for another pack format (many work across versions but rarely bump the number) show and load like any other: no red "made for an older version" entry, no confirmation, and Minecraft no longer drops such a pack from the enabled list at start, including a pack the player once confirmed as outdated (vanilla kept it on in options.txt but never loaded it). Like the Fabric mod No Resource Pack Warnings, whose NeoForge port excludes 1.21.1. |
| Ctrl+Q drops a stack on macOS | vanilla | On a Mac, Control + the drop key (Q) drops the whole stack, as on Windows and Linux. Minecraft's whole-stack drop on macOS is Cmd+Q, which also quits the game. Cmd+Q stays as it was. The fix is not applied at all on Windows and Linux. With MacOS Input Fixes installed, its logging drops to warnings (it writes two lines per scroll step), its options get the pack's values once (`macosInputFixes` in `config/haven-pack-defaults.json`: Cmd for game shortcuts) and Ctrl+Q still drops the stack in its Cmd mode. |
| Portal exits on the right side (server) | Immersive Portals | When the server refuses a player's portal crossing because it still waits for the client to confirm an earlier position correction (frequent with a high ping), the player goes back in front of the portal, not into its plane. From the plane, a small step back went through the frame's reverse portal and out of the wrong side of the destination portal. |
| Pipes into portals keep their look | Immersive Portals (Aeronautics fork), Create, TFMG | A Create fluid pipe that opens onto a portal draws Immersive Portals' connection piece and its filler pieces up to the portal plane, not an open rim with a gap. TFMG replaces the pipe model's methods, so Immersive Portals' own model mixin cannot apply; Haven Compat makes the same calls after TFMG's. |
| No NTGL error on join | NTGL (NukaTeam's Gun Lib) | Joining a server no longer logs a NullPointerException from NTGL: the projectile list the server sends is skipped on a client where NTGL never made the manager that would hold it (NTGL does not read that list on the client). |
| Block settings in Create schematics | Create; AE2, ExtendedAE, Pipez, Mekanism, Refined Storage, Storage Drawers, Trash Cans, Simple Magnets | Create copies no data of other mods' blocks into a print, so they printed with factory settings, and AE2 cables broke at once with a glass sound ([Create issue 5966](https://github.com/Creators-of-Create/Create/issues/5966)). Now a Schematicannon or creative print keeps each block's settings: AE2 parts and facades on their sides, bus and interface filters, modes, priorities, level emitter amounts, upgrade cards; Pipez extracting and cut sides and upgrades with their filters; Mekanism transmitter connections, machine side setup, ejector, redstone control, machine settings (sorter and miner filters) and upgrades; Refined Storage configuration (what its configuration card copies) and upgrades; Storage Drawers locks, shroud, priority, framing, upgrades and remote links; Trash Cans filters; coil ranges and filters. Never copied: stored contents, patterns, owners, frequencies and P2P links. The Schematicannon charges every upgrade and part it places (an upgrade with no recipe, such as a creative one, is left out). Links between blocks (filter destinations, remote drawer links) follow the print, and a rotated or mirrored schematic turns parts, side settings and blocks (AE2, Refined Storage, Pipez, coils). The Schematic and Quill's "save to file" asks the server for these settings, which the client does not have. |
| Trash slot always visible | TrashSlot, the pack's `config/haven-pack-defaults.json` | The trash slot stays on the screens the pack lists (`trashSlot.alwaysVisible`: TrashSlot's screen ids, the keys of `TrashSlotSaveState.json`, for example the player inventory). The toggle key does not hide it there. On other screens the toggle key works as before. |
| Key binding moves | the pack's `config/haven-pack-defaults.json` | When a mod update puts a key on one that another mod uses, the pack's `keys` rules move it once, from the old default to a free key, and only while the player still has the old default. A key the player bound themselves stays. The move works on the first launch. |
| Jade below the macOS notch | Jade, the pack's `config/haven-pack-defaults.json` | On a Mac, Jade's "what am I looking at" box moves down from the top edge (the pack's `jade.macos` value) so a MacBook's camera notch does not hide it, unless the player moved the box themselves. |

## Download

**[Latest build](https://github.com/Frosthaven/minecraft-haven-compat/releases/latest)**: drop `haven-compat.jar` into
`mods/` in the client instance, and on the server for the Mekanism fix. The Haven pack delivers it to both by itself
(`mods/haven-compat.pw.toml`, side both).

## The portal fix

Immersive Portals renders see-through portals in two ways: with stencil tricks on the main framebuffer when no shader
pack is active, and through its own portal framebuffers when Iris shaders are on. The shader path has two bugs that only
show on a strict driver without OpenGL 4.3, which today means every Mac:

1. Its normal shader renderer copies depth from the main framebuffer into the portal buffers with a blit, after turning
   the main framebuffer's stencil off. The portal buffers are packed depth+stencil; a plain depth buffer on Apple's
   driver is 32-bit float. The OpenGL spec makes a depth blit between different formats an error. PC drivers let it
   pass; Apple's reports `GL_INVALID_OPERATION`, and Immersive Portals answers by switching itself to its
   "compatibility" renderer.
2. The compatibility renderer copies depth and color with `glCopyImageSubData`, an OpenGL 4.3 function, with no
   capability check. On a driver without it LWJGL aborts the whole JVM ("No context is current or a function that is
   not available in the current context was called").

Haven Compat fixes both, and only where `glCopyImageSubData` is missing, so a computer with OpenGL 4.3 runs
Immersive Portals exactly as before:

- the main framebuffer keeps its stencil in the shader path, so Immersive Portals' own framebuffer hook gives it the
  same packed format as the portal buffers; the depth blit is legal, the normal renderer stays in use, and
  portal-in-portal views keep working;
- the two `glCopyImageSubData` copies become blits, so the compatibility renderer can never abort the game either;
- should Immersive Portals change the classes these patches name, the mod falls back to holding its render mode at
  `none` while shaders are on (portals still teleport, they show as empty frames) and tells the player once in chat.

Why this matters beyond one Mac: the pack's players are on Windows, Linux and macOS with very different OpenGL
drivers. A spec-legal depth copy and a capability check are what make the same jar behave the same on all of them;
lenient drivers hiding a spec violation on one platform is what turned a working feature into a crash on another. The
real fix belongs upstream (Immersive Portals: Aeronautics); this mod carries it until then.

`latest.log` tells what happened under `haven_compat`: `[portal depth]` lines for the capability check, the stencil
hold and each blit; `[portal guard]` lines for the fallback. `-Dhaven_compat.assumeNoCopyImage=true` runs the fallback
path on a computer that has the function, for testing.

## The portal first-look jitter

Iris builds one shader pipeline per dimension, on first use. With Immersive Portals that first use was the first frame a
portal into that dimension was in view: seconds of shader linking on the render thread (4 s for the Nether and 6 s for
the End with Complementary on an M1 Pro), then a full chunk rebuild. Once per dimension per session. This mod hooks the
moment Iris builds the pipeline of the dimension the player is in and builds the other two right there, then leaves the
player's active (a lookup, no rebuild). With Immersive Portals that moment is the rebuild after its switch to the Iris
renderer on the first world frame, behind the "Loading terrain" screen: the join takes those seconds longer, play does
not hitch. A shader pack reload builds them again the same way. `[iris prewarm]` lines in `latest.log` give each build's
time. Off without Iris or a shader pack; off by choice with `"portals": {"prewarmPipelines": false}` in
`config/haven-pack-defaults.json`.

## The server list fix

The pack declares its servers in `config/haven-pack-defaults.json`:

```json
{"servers": [{"name": "Haven", "address": "minecraft.thedragon.dev:9205", "previous": ["minecraft.thedragon.dev"]}]}
```

On the first client tick of a session, every entry of the player's list that is that server gets the current
address: an entry whose address is the current or a previous one, or that carries the pack's entry name. A server with
no entry is added at the top; an entry that became a duplicate goes. Other entries, the names and the order stay the
player's. To move the server to a new host or port, change `address` and put the old one into `previous`; the players'
entries follow on their next launch. No file or no `servers` key: nothing happens. `latest.log`: `[servers]` lines.

## Update popups

A mod that checks for its own updates nags a modpack player about something they cannot do (the pack pins the version)
and, as with Create Deep Seas' alpha 3.4.1 shown to everyone on 3.3.0, about builds the pack would not take. Haven Compat
cancels such a check at its start, one mixin per mod, applied only when that mod is installed. `latest.log`:
`[update nags]` lines.

## Jade below the notch

A full screen game on a MacBook with a camera notch draws under the notch, and Jade's box sits at the top centre, behind
it. Jade keeps the position per player in `config/jade/jade.json` (`overlay.overlayPosY`, 1.0 = top edge), which the pack
ships. The pack declares in `config/haven-pack-defaults.json`:

```json
{"jade": {"packValue": 1.0, "macos": 0.94, "force": false}}
```

On macOS, on the first client tick, the box moves to `macos` while it still stands at `packValue`; a box the player moved
stays (`force: true` moves it every launch). Other systems: nothing. `-Dhaven_compat.assumeMac=true` runs the macOS path
elsewhere, for testing. `latest.log`: `[jade notch]` lines.

## Pack defaults

The pack's one-time defaults, moved here from Haven Create UI Remix (an optional mod; this one is required). While the
game starts, right after Minecraft reads the selected resource packs and before it loads them: the resource packs listed
in `config/haven-pack-defaults.json` (`resourcePacks`) are enabled once, in order; order fixes (`resourcePackOrder`: `id`
right above `above`) move a pack once, when both are on, and the player's later order stays; `darkLoadingScreen: true`
switches the Monochrome Logo option on once, so NeoForge's early loading window (it reads `darkMojangStudiosBackground`
from options.txt) and Mojang's loading screen are black, not red, from the next launch. Only those lines of options.txt
change (0.11.0 to 0.17.0 saved the whole file at that point, before mods add their key bindings, and every mod key
binding went back to its default). Done there, a pack change costs
no extra resource reload (up to 0.10.0 these ran on the title screen, and Minecraft reloaded every resource behind a
third progress bar). On the first title screen of a session, the first run sets Iris's shader pack per OS (`shader`). At mod construction, before Iris loads its
pack, per-OS shader pack options (`shaderOptions`) are written into `shaderpacks/<pack>.txt`: a key the file lacks, or
one that still holds the value written before, gets the pack's value; a value the player changed stays. First use:
Apple's OpenGL stops at 16 texture units, and with Create Deep Seas 3.4's hull sampler Complementary at Block Reflection
Quality High overflows (Iris: "No more available texture units", shaders off), so macOS runs it at Low. State in
`config/haven-pack-defaults.state.json`, the same file and keys as before, so existing installs apply nothing again.

## Mekanism block updates

Mekanism sends a block's update packet (`PacketUpdateTile`: a fluid tank's fill level, machine states) only when
`PacketUtils.hasPlayersTracking` finds a player whose vanilla chunk tracking view holds the chunk
(`ChunkMap.isChunkTracked`). Immersive Portals cancels vanilla's `applyChunkTrackingView` and tracks chunks itself, so
every view stays empty and Mekanism sent nothing. The client kept the tank's fluid from the chunk data but its fill
level at zero, and Mekanism draws no fluid at level zero. Haven Compat answers that check from `ChunkMap.getPlayers`,
which Immersive Portals overwrites with its own tracking and which NeoForge sends the packet through. Server side, only
when both mods are installed; in single player the built-in server takes it from the client's copy.

## Refused portal crossings

The client teleports itself when its eye crosses a portal, then sends the crossing to the server. Immersive Portals'
`ServerTeleportationManager.onPlayerTeleportedInClient` refuses it while the server waits for the client to confirm a
position correction (`has awaiting teleport`) and puts the player at the server's position. That position is the
client's last move, 1 to 3 cm behind the portal plane. Each frame holds two portals, one for each side, so the player
now stands in front of the reverse one, and a small step back takes it: the player arrives on the other side of the
destination portal. Haven Compat wraps that call: for this reason only, in the same dimension, the player moves along the
portal's normal until the eye is 10 cm in front of the plane, when no block is in the way.
Server side, only with Immersive Portals installed.

## Development

`./gradlew build` (Gradle on JDK 17 or 21, mod on Java 21). Other mods are reached by class name only (mixin targets,
reflection), never on the compile classpath. Every push to `main` publishes a rolling `latest` release and an immutable
`v<version>-<commit>` release, which the Haven pack pins by hash (`just bump-mod haven-compat` in minecraft-haven-pack).
