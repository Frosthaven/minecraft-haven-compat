# haven-compat (`haven_compat`)

NeoForge 1.21.1 mod for the Haven pack, client side except two server mixins (`mekanism`, `portals/RefusedCrossing`) (minecraft-haven-pack): fixes between other mods, one package per
fix. Read README.md first. Rules: every fix is off when its mods are absent; other mods are reached by class name only
(`@Mixin(targets = "...")`, reflection), never as a compile dependency; nothing logs per frame (`Log.once`); a fix that
cannot apply logs it and leaves the game as it was, never a crash of its own.

## Layout (`src/main/java/dev/frosthaven/havencompat`)

- `HavenCompat`: the entry point, registers each fix.
- `Log`: once-per-key logger (logger `haven_compat`), one channel prefix per fix.
- `portals/PortalDepth`: the portal fix's state and GL work. `noCopyImage()` decides once on the render thread
  (`GL.getCapabilities().glCopyImageSubData == 0`, or the flag `-Dhaven_compat.assumeNoCopyImage=true`);
  `keepMainStencil` (the stencil hold), `blitDepthStencil` / `blitColor` (the copies; the stencil bit only when both
  framebuffers have one; read/draw bindings restored; the first failure logged with both depth formats);
  `patched()` = both mixins applied (recorded by the mixin plugin's `postApply`).
- `portals/PortalShaderGuard`: the fallback, a client tick every 10: where the call is missing and `patched()` is false,
  `IPGlobal.renderMode` is held at `none` while `Iris.getCurrentPack()` is present (reflection), the player is told
  once (`haven_compat.portals.shaders_off`), the mode comes back when shaders go off. `register` defines the two target
  classes (`Class.forName(name, false, ...)`) so the mixins apply before the first tick asks.
- `portals/PipelinePrewarm` + `mixin/IrisPipelinePrewarmMixin`: `@Pseudo` mixin on Iris's `PipelineManager.preparePipeline`
  (HEAD notes the ids in `pipelinesPerDimension`, RETURN finds the new one and calls `PipelinePrewarm.created`). It then
  builds the other two of overworld, the_nether, the_end with `preparePipeline` (reflection) and the player's again (a
  lookup, no `allChanged()`); one log line per build with ms. Guards: re-entry (`active()`), a vanilla pipeline (shaders
  off), no level (the title screen's build), and with Immersive Portals loaded only once `IPCGlobal.renderer` is its Iris
  renderer (its first switch destroys every pipeline; so the build happens at the recreation right after it, on the
  first world frame, behind the loading screen). Off: `portals.prewarmPipelines = false` in haven-pack-defaults.json.
- `packs/OldPacks` + `OldPacksOptionsMixin` / `OldPacksEntryMixin` (client, vanilla only): `PackCompatibility.isCompatible()`
  reads true in `Options.updateResourcePacks/loadSelectedResourcePacks` and `TransferableSelectionList$PackEntry.render/
  handlePackSelection` (MixinExtras `@ModifyExpressionValue`). HEAD of `loadSelectedResourcePacks`: `forgetIncompatible`
  (`IncompatibleList`, pure, unit tested) takes installed packs off `incompatibleResourcePacks`; vanilla's branch for a listed pack that is
  now compatible takes it off that list but never selects it, and options.txt keeps the entry until an options save, so
  the pack never loaded (3D redstone wires, 0.18.2).
- `schematics/` (both sides: `HavenCompatCommon`, a second `@Mod` class without `dist`, since `HavenCompat` is client
  only). Create copies block entity data into a print only for `create:safe_nbt` blocks and registered writers
  (`BlockHelper.prepareBlockEntityData`), so other mods' blocks printed with factory settings (an empty AE2 cable bus
  breaks itself on its first tick, `CableBusBlockEntity.onReady`). `SchematicFixes` registers at common setup, per mod
  present; each mod is a `BlockData` (`copy`: settings and the real items they hold, never contents, owners or
  frequencies; `items`: charged; `turn`: data kept per absolute side; `positions`: links), registered by block entity
  id through `SchematicCopy` (Create's `SafeNbtWriterRegistry`, `SchematicRequirementRegistries.BLOCK_ENTITIES`,
  `MovedBlockTransformerRegistries.BLOCK_ENTITY_TRANSFORMERS`; a namespace skips types with a writer of their own).
  Only `AeCableBus` and `AeBlockTurn` link against a mod (`compileOnly` Create 6.0.10 slim and AE2 19.2.17, never
  shipped); the others work on NBT keys and reflection (`MekanismData`: `writeSustainedData`; `RsData`:
  `writeConfiguration`). `Obtainable`: an item no server recipe makes is not copied or charged. AE2: the cable bus
  keeps its own `SchematicRequirementRegistries.BLOCKS` (parts, facades strict); `AeSettings` is the key whitelist for
  parts and AE2 / ExtendedAE blocks. Turns: vanilla `StructureTemplate.placeInWorld` turns states through
  `rotate`/`mirror`, then Create's `SchematicPrinter` runs `StructureTransform.apply` on each block entity. Blocks
  without a right `rotate`/`mirror` get mixins (`AeBlockTurnMixin`, `PipezPipeTurnMixin`, `MagnetCoilTurnMixin`,
  `RsDirectionTurnMixin`: RS answers every `rotate` with one wrench step, so only while `SavedPositions.placing()`;
  `StateTurn` by property name). Links: `SchematicExportMixin` (after `fillFromWorld`) stores them relative to the
  schematic with a marker, `StructurePlaceMixin` (`processBlockInfos` RETURN) moves them like block positions;
  unmarked (older) ones are dropped. Client "save to file" reads the client world, whose block entities lack most
  settings: `SchematicQuillSaveMixin` asks the server first (`ServerFill`, payloads `schematic_data_request` /
  `schematic_data`, needs the quill in hand), `ServerFillClient` saves again with the reply merged (100 ticks at
  most, then the plain save). Test: a throwaway server mod builds one block of each case with settings, upgrades and
  contents, saves through `SchematicExport` to `schematics/uploaded/<owner>/`, prints through `SchematicPrinter` for
  all 12 rotation x mirror pairs, checks settings, turns, links, no contents and the charged items (each with a
  recipe); a client half saves with the quill and compares the file with a raw client save. AE2 alone: 0.17.0 0/12,
  0.18.0 5/12, 0.18.1 12/12; all mods: 0.19.0 12/12. The spin for a turned front and top is searched
  (`AeBlockTurn.spin`): `BlockOrientation.get(front, up)` can return a spin whose top is not `up`. Storage Drawers:
  drawers beside a controller store no link (the controller finds them, `updateCache` on load); only remote upgrades do.
- `defaults/LoadDefaults` writes options.txt as text (`OptionsFile`), never `Options.save()`: at
  `Options.loadSelectedResourcePacks` only vanilla key mappings exist, and a save there dropped every mod key binding.
- `PackDefaults`: the pack's `config/haven-pack-defaults.json` (`file()`, `section(file, key)`, `root(file)`); each fix
  reads its own key. `Os.macOS()`: the OS check (`os.name`, or `-Dhaven_compat.assumeMac=true`).
- `defaults/`: the pack's one-time defaults (from haven-create-ui-ux up to 1.3.0). `DefaultsState`: `config/
  haven-pack-defaults.state.json` (keys `seen`, `shaderApplied`, `shaderOptions`, `ordered`, `darkLoadingScreen`). `LoadDefaults`
  (`mixin/LoadDefaultsMixin`, TAIL of `Options.loadSelectedResourcePacks`, once; before the first resource load, so no
  reload): `resourcePacks` enabled once in order (`PackList` pure, unit tested), `resourcePackOrder` rules once each (state
  `ordered`, `PackOrder` pure, unit tested), `darkLoadingScreen` sets `darkMojangStudiosBackground` once; the pack lists
  are copied back into Options as `updateResourcePacks` does (without its reload), then `options.save()`.
  `TitleDefaults`: first title screen, `shader` set per OS on the first run (Iris by reflection:
  `getIrisConfig().setShaderPackName/setShadersEnabled/save`, `reload`). `ShaderOptions`: at mod construction (before Iris
  loads its pack), `shaderOptions.<macos|default>.<pack>.<KEY>` written into `shaderpacks/<pack>.txt` (java.util.Properties);
  `OptionChoice.decide(have, before, want)` (pure, unit tested): unset or still ours -> write, the player's -> stays.
- `jade/JadeNotch`: macOS only (`os.name`, or `-Dhaven_compat.assumeMac=true`): first client tick, reads `jade`
  (`packValue`, `macos`, `force`), `jade/NotchMove.shouldMove` (pure, unit tested) decides, Jade's API by reflection through its
  interfaces (`IWailaConfig.get().getOverlay()`, `IWailaConfig$IConfigOverlay.get/setOverlayPosY`, `Jade.CONFIG` a
  `JsonConfig`, `save()`), registered only with `jade` loaded.
- `keys/MacDropStack` + `mixin/MacDropStackMixin` / `MacDropStackScreenMixin` (client, vanilla only, macOS only: the plugin's
  `MAC_ONLY` skips them unless `Os.macOS()`): the whole-stack choice of a drop also reads true with a Control key held:
  `LocalPlayer.drop(Z)`'s argument in `Minecraft.handleKeybinds` (`@ModifyArg`, mixin priority 1100: MacOS Input Fixes
  replaces that argument with its own modifier, so ours must apply after it) and `hasControlDown()` in
  `AbstractContainerScreen.keyPressed`.
  `keys/MacInputLogs`: with `macos_input_fixes` loaded, log4j `Configurator.setLevel("com.hamarb123.macos_input_fixes", WARN)`
  at construction (that mod logs INFO per scroll event). `keys/MacInputOptions` (macOS, that mod loaded): pack key
  `macosInputFixes` (`{"useCommandKey": "true"}`) into `config/macos_input_fixes.txt` (`key:value` lines, read by that mod
  at client setup) at construction; the first time it replaces the mod's own value, then `keys/OptionLines` (pure, unit tested; then `OptionChoice`).
- `servers/ServerMatch`: the server list fix's decisions, pure (unit tested, `./gradlew test`): `plan(have, want)` =
  sets (an entry that is the pack's server by current or previous address or by the pack's entry name, with another
  address), removes (duplicates after the sets, highest index first), adds (no entry at all). `host:25565` = `host`.
- `servers/ServerListSync`: reads `servers` of `config/haven-pack-defaults.json`, applies the plan to Minecraft's
  `ServerList` on the first client tick (`ClientTickEvent.Post`; the title screen is not a given: a quick-play launch,
  or a mod's own screen first, as Create: Submarine's update screen in the test instance), saves only on a change; any error is logged and
  the list stays as it was.
- `mixin/PortalStencilHoldMixin`: `@ModifyVariable` on the boolean of `IPPortingLibCompat.setIsStencilEnabled`.
- `mixin/PortalDepthCopyMixin`: `@Inject(HEAD, cancellable)` on `IPIrisHelper.newCopyDepthStencil` and `copyColor`.
- `updates/UpdateNags`: the update-popup fix's log and the Create Deep Seas target class name; `mixin/DeepSeasUpdateCheckMixin`
  cancels `UpdateChecker.check()` at HEAD (no Modrinth request, `updateAvailable` stays false, `DeepSeasUpdateScreen`
  never opens).
- `mekanism/ChunkWatchers` + `mixin/MekanismChunkTrackingMixin` (the only server mixin, in the config's `mixins` list; the
  `@Mod` class is `dist = CLIENT`, so a dedicated server builds no class of ours but applies this mixin):
  `PacketUtils.hasPlayersTracking` answers `!chunkMap.getPlayers(chunk, false).isEmpty()` (Immersive Portals' tracking).
  Test: a Mekanism fluid tank with fluid shows it (single player applies it through the built-in server).
- `portals/RefusedCrossing` + `CrossingBack` (pure math, unit test) + `mixin/RefusedCrossingMixin` (server, `mixins` list):
  `@WrapOperation` on `teleportEntityGeneral` in IP's `onPlayerTeleportedInClient`, `@Local` args and the `failReason`
  String; the portal comes from the `portalId` argument, its plane from public `getOriginPos()`/`getNormal()` (reflection).
  The eye moves along the normal to 0.1 in front (front = the side the normal points to: IP teleports on front -> back).
  The packet's "eye before teleportation" is the eye at the end of the crossing tick, already behind the plane: never use it
  as the way back. Only for the reason text "has awaiting teleport" (an upstream rename turns the fix off).
  Test (2026-10-21): a throwaway server mod held the client's confirmation and set the server position 2 cm past the
  plane on the crossing request, then nudged the player 0.15 back; without the fix the nudge crossed the reverse portal.
- `portals/PortalPipeModel` + `mixin/PortalPipesTfmgMixin` (client, priority 1500; needs `immersive_portals_core`, `tfmg`,
  `create`): TFMG `@Overwrite`s Create's `PipeAttachmentModel.gatherModelData` and redefines `getQuads`, and Mixin refuses
  IP's injections into methods merged at the same priority (1000), so IP 2.4.15's `MixinPipeAttachmentModel_PortalPipes`
  fails as a whole. Ours repeats its three hooks (rim attachment wrap, portal faces at RETURN, filler quads at RETURN) and
  calls IP's public `IPCreatePortalPipeModel` through method handles; it does nothing when `Mixins.getMixinsForClass` lists
  IP's mixin (a failed one still leaves its `ip$...` handler methods merged, so their presence proves nothing). Test: a fluid pipe ending at a portal shows a connection
  piece reaching the plane, not a rim (screenshots 2026-10-21).
- `mixin/NtglProjectileSyncMixin` (client; `ntgl`): NTGL 3.2.0 makes `NetworkProjectileManager` only in the server's
  `AddReloadListenerEvent`, so `ClientPlayHandler.handleUpdateProjectile` hit null on every dedicated-server join. HEAD
  inject without target args (no NTGL types on the classpath), `Class.forName` + `get()` by reflection, cancel when null.
  `getConfig` has no callers, so the client loses nothing. Test: no "NetworkProjectileManager" NPE in a client log on join.
- `mixin/CompatMixinPlugin`: each mixin applies only with all its mods in `LoadingModList` (`MODS_OF`: mixin simple name ->
  mod ids); `postApply` -> `PortalDepth.patched(target)`. The config is `required: false` with `defaultRequire: 1`: a target that moved fails
  that one mixin loudly in the log and the guard takes over.

## Why the portal fix is shaped this way (Immersive Portals: Aeronautics 2.2.4, read from its jar)

`PortalRenderer.switchToCorrectRenderer`: Iris present and shaders on -> `IPGlobal.renderMode` normal =
`IrisPortalRenderer`, compatibility = `IrisCompatibilityPortalRenderer`, none = `rendererDummy`. Every Iris renderer's
`prepareRendering` ends with `IPPortingLibCompat.setIsStencilEnabled(mainRenderTarget, false)`, after giving its own
`SecondaryFrameBuffer`s a stencil (`MixinRenderTarget.modifyTexImage2D`: `GL_DEPTH24_STENCIL8`, or
`GL_DEPTH32F_STENCIL8` when `IPCGlobal.useSeparatedStencilFormat`, chosen from the GPU vendor and the main depth
format; `MixinMainTarget` does the same for the main framebuffer when its flag is on). `IrisPortalRenderer.doMainRenderings`
then blits depth main -> deferred; on `glGetError() != 0` it logs "[ImmPtl] Depth blit ... failed with GL error 0x{}",
sets `renderMode = compatibility` and chats "Switched to compatibility portal rendering mode". The Mac log of
2026-10-17: main depth `0x8cac` (GL_DEPTH_COMPONENT32F, Apple's choice for an unsized depth texture), deferred
`0x88f0` (GL_DEPTH24_STENCIL8), error `0x502`. `IPIrisDepthFormatFix.matchDeferredDepthFormat` would convert the
deferred texture but gives up unless both are packed depth+stencil, hence the hold on the main one. Fourteen seconds
later `IrisCompatibilityPortalRenderer.onBeforeHandRendering` -> `IPIrisHelper.newCopyDepthStencil` ->
`GL43C.glCopyImageSubData` aborted the JVM. The jar's only OpenGL 4.x call is that one (`javap` over every class);
`IPIrisHelper` even has an unused `isCopyImageSubDataSupported()` and a blit-based `copyDepthStencil`. Iris itself
handles a stencil main framebuffer (`DepthCopyStrategy.Gl30BlitFbCombinedDepthStencil`, an integration-test mixin for it).

## Testing

No Mac here. The fallback path runs on any machine with `-Dhaven_compat.assumeNoCopyImage=true`: Prism instance
"Haven Portals Test" (a Haven client with the snapshot world "Haven Snapshot 2026-10-16", the player standing at
-992 63 554 facing the lit portal at -987 64 554, Iris on with Complementary Unbound, `pauseOnLostFocus:false`),
this jar in `mods/`, `OverrideJavaArgs=true` + the flag in `instance.cfg`, then
`prismlauncher -l "Haven Portals Test" -w "Haven Snapshot 2026-10-16"` and a screenshot (`grim`). Pass = the log has
`[portal depth]` "copied by blit" or the stencil hold line, "switched to renderer class ...IrisPortalRenderer" and no
"Switched to compatibility" / "Depth blit ... failed", and the screenshot shows the other side through the portal.
Close the game by working folder (`/proc/<pid>/cwd`), never by name; close Prism before editing `instance.cfg`.
A real Mac check (shaders on, walk through a portal, portal-in-portal) is still owed after each Immersive Portals update.

Server list: the "Haven Portals Test" instance has the bare `minecraft.thedragon.dev` entry in its `servers.dat`
(the old default); with this jar and `servers` in its `config/haven-pack-defaults.json`, a launch to the title screen
logs `[servers] "Haven": minecraft.thedragon.dev -> minecraft.thedragon.dev:9205` and the file carries the port
(checked 2026-10-18, 0.2.0).

Update popups: the same instance (Create Deep Seas 3.3.0, Modrinth has 3.4.x) logs `[update nags] Create Deep Seas:
its own update check is off` and reaches the title screen with no `DeepSeasUpdateScreen` frame in the log (without the
mixin that screen sat in front of the title screen; checked 2026-10-18, 0.3.0).

Jade notch: no Mac here; `-Dhaven_compat.assumeMac=true` in the test instance's JVM args with `jade.macos` in its
`config/haven-pack-defaults.json`: the log gets `[jade notch] macOS: Jade overlay Y 1.0 -> 0.94 (camera notch)` and
`config/jade/jade.json` carries `overlayPosY: 0.94`; a second launch logs "left alone" (already there); set 0.8 by hand and
it stays (checked 2026-10-18, 0.4.0). The real notch check is a Mac player's: box visible below the notch in full screen.

## Release

Push to `main`: CI publishes `haven-compat.jar` as the rolling `latest` release and `haven-compat-<version>.jar` as an
immutable `v<mod_version>-<sha7>` release (the Haven pack pins one with `just bump-mod haven-compat`). Bump
`mod_version` in `gradle.properties`. Commits: author `Frosthaven <shane@thedragon.dev>`, no Co-Authored-By trailer.
