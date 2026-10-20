# haven-compat (`haven_compat`)

NeoForge 1.21.1 mod for the Haven pack, client side except one server mixin (`mekanism`) (minecraft-haven-pack): fixes between other mods, one package per
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
  handlePackSelection` (MixinExtras `@ModifyExpressionValue`).
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
  `MAC_ONLY` skips them unless `Os.macOS()`): `Screen.hasControlDown()` (Cmd on macOS) in `Minecraft.handleKeybinds` and
  `AbstractContainerScreen.keyPressed` (the drop key's whole-stack choice) also reads true with a Control key held.
  `keys/MacInputLogs`: with `macos_input_fixes` loaded, log4j `Configurator.setLevel("com.hamarb123.macos_input_fixes", WARN)`
  at construction (that mod logs INFO per scroll event).
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
