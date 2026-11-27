# haven-compat (`haven_compat`)

NeoForge 1.21.1 mod for the Haven pack, client side except the server mixins (`mekanism`, `portals/RefusedCrossing`, `contraptions/ContraptionSpawn`, `schematics/PrintOwner` and others in the `mixins` list) (minecraft-haven-pack): fixes between other mods, one package per
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
  Portal views (0.31.2): Immersive Portals 2.8 keeps three more pipeline maps (`IPIrisPortalViewPipelines.PIPELINES`)
  and swaps one into the manager (`MixinIrisPipelineManager_PortalViews`, a `@WrapMethod` on `preparePipeline`) for the
  second and later views of a dimension in a frame; the player's own dimension seen back through a far portal is view 1.
  It builds them on first look (0.3 to 0.5 s each, in play). Our RETURN hook ran inside that wrap, saw a new id in the
  swapped map and prewarmed the other two dimensions into it: 6 builds, 3.2 s, at the first look at a nether hub
  (2026-11-14, NVIDIA). Now a creation into one of those maps starts nothing, and a fresh set of pipelines (join,
  shader reload) also builds the first `portals.prewarmPortalViews` (default 2) views of the player's dimension: the
  mixin makes the map `@Mutable` and `PipelinePrewarm.Pipelines` swaps it, then Immersive Portals' `onCreated`. Other
  views stay on first look: each pipeline holds full-size render targets. Test (hidden game, 2026-11-14): the headless
  display fails Immersive Portals' depth blit and it falls back to the compatibility renderer (no portal in portal);
  `-Dhaven_compat.assumeNoCopyImage=true` in the instance's JVM arguments keeps the normal renderer. Scene: a nether
  portal, a second one lit 10 blocks behind its nether side; no "Creating pipeline" line after the join.
- Leaks at a rejoin (`leaks/LevelUnloads` + `mixin/IpLevelUnloadMixin` on `ClientWorldLoader.cleanUp` and
  `disposeDimensionDynamically`: LevelEvent.Unload for Immersive Portals' other client levels; `leaks/NtglPlayers`:
  NTGL's `lastSelectedSlots`; `colorwheel/ColorwheelWarm.worldLeft`: Flywheel's resetAll at a logout). Check: join, leave
  and join again, `jcmd <pid> GC.class_histogram`: 3 ClientLevel and 1 LocalPlayer (was 6 and 2). To find a holder:
  `jcmd <pid> GC.heap_dump`, Eclipse MAT `ParseHeapDump.sh <hprof> "-command=path2gc 0x<address> -excludes
  java.lang.ref.WeakReference:referent java.lang.ref.SoftReference:referent" -format=txt -unzip org.eclipse.mat.api:query`.
- Waypoint icons (`journeymap/WaypointIcons`, `mixin/JourneyMapIconMixin` on `TextureCache.getWaypointIcon`'s
  `resolveImage`, `mixin/JourneyMapIconButtonMixin` on `ImageSelectButton.renderWidget`'s `getTexture`): a missing icon
  texture falls back to `journeymap:textures/waypoint/icon/waypoint-icon.png`. Reproduce: a waypoint with icon
  `tfmg:textures/item/converter.png` (ClientWaypointFactoryImpl.at, getIcon().setResourceLocation), open it in the editor,
  save, open the waypoint manager; without the fix the game crashes (NPE "from"/"texture" is null).
- `journeymap/MapSecondKey` (0.39.0, client, `journeymap`): `key.haven_compat.open_map`, default M, IN_GAME, JourneyMap's
  category (`jm.common.hotkeys_keybinding_category`). Mirrors JourneyMap 6.0.10's J (`KeybindingHandler`): in play
  `UIManager.getOrOpenFullscreenMap()`, on `Fullscreen` with no chat and no focused search `UIManager.closeAll()`
  (`ScreenEvent.KeyPressed.Pre`, cancelled). Reflection only; `[map key]` once. Structurize's `mirror` is also on M
  but its conflict context is active only in a blueprint manipulation window: no clash.
  `journeymap/JourneyMapSplash` + `mixin/JourneyMapSplashMixin` (HEAD of `Fullscreen.init()V`): sets
  `CoreProperties.splashViewed` to the running version (StringField set + save, as the About screen does), so the
  "About JourneyMap" splash never covers the map; the About button still works. `[journeymap splash]` when it acts.
  Test (2026-11-24, visible nested sway, copy of the traintest rig, JourneyMap 6.0.10, no `journeymap/config`;
  presses through `KeyboardHandler.keyPress`, KubeJS client hook, `Screenshot.grab`): M opens the map with no splash,
  J on the map closes it, J opens, M on the map closes.
- `mekanism/ModeHand` + `mixin/MekanismModeHandMixin` (0.39.0, client, `mekanism`; HEAD of `MekanismKeyHandler`
  `lambda$static$0`, the `key.mekanism.mode` key-down action, require 0): Mekanism 10.7.19 changes the main hand's
  mode item, or the off hand's when the main hand holds none. Haven binds it and NTGL's fire select to ";": gun in
  the main hand + fluid tank in the off hand switched both. Now: off hand only while sneaking, else the main hand only
  (nothing when it has no mode item); sneaking with no off-hand mode item keeps Mekanism's reverse step on the main
  hand, except (0.40.0) when the off hand holds an NTGL gun (`IWeapon`, by class name): that press is NTGL's fire select
  on the off-hand gun, so Mekanism does nothing. Radial items (Configurator etc.) are not stepped by a press, as in Mekanism; its radial (key held,
  `ClientTickHandler`: main hand first, then off hand; sneak not read) is unchanged. `[mekanism mode hand]` at the
  first press. Test (same run): gun + tank, ";" -> gun auto to semi, tank unchanged; sneak + ";" -> tank bucket mode
  on, and the gun switched too (NTGL's queued click, fixed by `ntgl/NtglKeyQueue`). Holding a key by `setDown` every tick
  makes the radial open and close each tick (flashing): never hold a screen-opening key that way in a visible test.
- `ntgl/NtglKeyQueue` + `mixin/NtglKeyQueueMixin` (0.39.0, client, `ntgl`; RETURN of `InputHandler.handleKeys`,
  require 0): NTGL 3.2.0 reads attachments, inspect, fire select, tips and debug show with `consumeClick()` only when
  the hand it picks (off hand while sneaking, else main hand) holds an `IWeapon`; otherwise the click stayed queued and
  fired later (sneak + ";" with a tank in the off hand: the main-hand gun switched once sneaking ended). The left-over
  clicks of those keys are dropped at the end of each `handleKeys`. `[ntgl key queue]` at the first dropped press.
- `ntgl/NtglAltHand` + `mixin/NtglAltHandMixin` (0.40.0, client, `ntgl`; HEAD of `InputHandler.handleInput(WeaponData,
  InteractionHand, KeyMapping)`, require 0): NTGL 3.2.0's `handleWeaponKeys` gives `key.ntgl.alt` to the off-hand gun when the
  main hand holds no weapon (and `canUseOffhandWeapon`: a one-handed gun). Haven binds it to G with Create's item menus,
  so G with a Create item in the main hand also worked the off-hand gun. The off-hand call for the alt key is skipped
  while the main hand holds no `IWeapon`; the main-hand path and other off-hand keys are unchanged. `[ntgl alt hand]`
  when it first skips.
  Test of 0.40.0 (2026-11-24, visible nested sway, copy of the traintest rig, server in creative with force-gamemode (the
  snapshot's spawn is in fire: a survival player died before the probe ran), a throwaway client mod: presses through
  `KeyboardHandler.keyPress` / `MouseHandler.onPress`, sneak = the sneak key's own key, item state from the stack's
  component patch, `Screenshot.grab`, a stop with a log line on death): mouse 4 bound to Mekanism's mode and NTGL's fire
  select: tank main -> bucket mode on; nailgun main -> fire mode semi; sneak, nailgun main + tank off -> only the tank;
  sneak, tank main + nailgun off -> only the gun (ModeHand); G (NTGL alt + Create tool menu), wrench main + one-handed
  `ntgl:classic10mm` off -> `[ntgl alt hand]`, gun unchanged; chest mode on the middle button (from `haven-keys.txt`) with a
  jetpack -> HOVER. Move rules G -> ' -> `key.keyboard.7:ALT` ended on Alt+7; the import changed its 3 lines and nothing else.
- `animations/SpeedStep` (pure, unit tested) + `AnimSpeedGuard` (log) + `mixin/AnimSpeedGuardMixin` (0.41.0, client,
  `playeranimator`; `@ModifyVariable` at HEAD of `SpeedModifier.step(F)V`, require 0): playerAnimator 2.0.4's step
  loops `while (delta > 1) { delta--; tick(); }` on `delta * speed + shiftedDelta`; `speed` is a public field. NTGL
  3.2.0's `PlayerAnimationHelper.playAnim` sets it to `animation.getLength() / length`; `PlayerAnimations.playReloadAnimation`
  gives length 0 for a per-cartridge gun the client sees as full (`maxAmmo - ammoCount` cartridges). An ammo switch
  (`ServerPlayHandler.handleAmmoChange`: unload, then `handleReload`) sends the reload animation while the client still
  has the full magazine: speed +Infinity, the render thread hung for good (2026-11-24, Create Gunsmithing shotgun with
  the pump, 10/10, switch to Flechette; a plain reload of a full gun starts no reload, so no animation). Every client
  that sees the player runs that animation. The guard: a speed that is not finite plays at 1, a remainder that is not
  finite or above 1 is 0, one step advances at most 200 ticks. `[anim speed guard]` once with the values.
- `ntgl/NtglReloadWheel` + `WheelEntries` (pure, unit tested) + `mixin/NtglReloadWheelMixin` (0.41.0, client, `ntgl`;
  INVOKE `ActionWheelManager.showWheel` in `InputHandler.reloadUnload(Z)V`, cancellable, `@Local` heldItem, modes
  (ArrayList ordinal 0) and actions (ordinal 1), require 0): NTGL lists every mode with max ammo above 0 and adds
  PRIMARY again; Create Gunsmithing's pump and drum set max ammo for every mode, so the shotgun's scope mode
  (`ntgl:secondary`, action `ntgl:scope`) got a second "Main Weapon" entry for the same magazine (`AMMO_COUNT` is one
  component for all modes). Entries are matched to modes by colour (`WheelAction.getColor` = `WeaponMode.getColor`);
  scope modes and repeated modes are left out; one entry left: its action runs at once (for the shotgun
  `startReloading(PRIMARY)` / `unloadAmmo(hand, PRIMARY)`, what a tap ran: press opened the wheel, release ran the
  default). Unload uses the same path. `[reload wheel]` once per key.
- `ntgl/NtglAmmoSelectHand` + `mixin/NtglAmmoSelectHandMixin` (0.41.0, client, `ntgl`; HEAD of
  `InputHandler.selectAmmoKeyPressed()V`, cancellable, require 0): NTGL 3.2.0 showed the ammo wheel on a press of
  `key.ntgl.ammo_select` (C in the pack) with any item in the hand (off hand while sneaking, else main hand), and its
  `MouseHandlerMixin` cancels `turnPlayer` while a wheel is shown: held with no gun, mouse look froze on an empty wheel.
  Now the press is skipped unless that hand holds an `IWeapon` (`NtglAltHand.isWeapon`); the release's `closeWheel`
  finds no wheel. `[ammo select hand]` once. With a gun and no other ammo NTGL still shows an empty wheel (unchanged).
- 0.37.0, portals with shaders (found with the harness in `minecraft-rancher-companion/run/traintest`: a snapshot of the
  live world's rail regions, a server container, a copy of Haven, a probe mod that rides a train through two nether
  portals and samples the render thread on a long frame; `run.py <label> --nowalk [--pure] [--title] [--ahead]`):
  - `portals/WarmQueue` (steps, one a frame, `percent()`), `portals/CompileScreen` + `portals/StartupWarm`
    (`PipelinePrewarm.startup()` when the title screen opens: IP's `PortalRenderer.switchToCorrectRenderer()`, then
    every pipeline, portal view and Colorwheel source), `mixin/ConnectAfterCompileMixin` (a connection waits),
    `mixin/IrisKeepPipelinesMixin` + `mixin/IrisCurrentDimensionMixin` (Iris's `updateLevelInEngines` handler compares
    `getCurrentDimension()` with `lastDimension` by identity and destroyed everything at every join; while
    `Minecraft.setLevel` runs the two are made the same object). `PrewarmScreen.draw` (black, bar, "Loading terrain").
  - `ColorwheelWarm.queueSpares` + `mixin/ColorwheelSpareMixin`: a `ClrwlDrawManager` needs only the pack and the program
    set; built ahead per dimension, given to `ClrwlEngine.<init>`, and taken back at `ClrwlEngine.delete` (Flywheel
    makes, deletes and makes again a level's engine at a join). Pairs: an engine's own pipelines plus those of each
    dimension in `PipelinePrewarm.LINKS` (a carriage's far half is drawn by level A's engine with dimension B's
    pipeline; that was the 1 to 1.8 s freeze at a train's first crossings).
  - `colorwheel/ProgramBinaries` + `mixin/ColorwheelLinkerMixin`: `glProgramBinary` in place of `glLinkProgram`, key =
    SHA-1 of the attached shaders' sources and the driver strings; saved again 90 s into play (the binary then holds
    the code the driver finishes at a first draw). `ColorwheelWarm.uniforms` asks for every uniform location.
  - `mixin/ColorwheelCullMixin`: `ClrwlPackDirectives.getOcclusionCulling/getFrustumCulling` read false for GBUFFERS with
    Immersive Portals (per-engine cull state, several cameras a frame). `colorwheel/ColorwheelFrame` + three mixins:
    Colorwheel's shared frame and pass uniform buffers are written again before a draw of another view.
  - `portals/LatePassengers` + `mixin/LatePassengersMixin` (on `ClientboundSetPassengersPacket.handle`, because ntgl
    cancels `handleSetEntityPassengersPacket` from its head): reseat within 1.5 s, keep an unusable list 10 s.
  - `portals/PortalLookAround` + mixin (TAIL of `LevelRenderer.renderLevel`: `needsUpdate(); setupRender(camera, look,
    false, false); needsUpdate()`; a first version swapped the drawing's own frustum and the portal view flickered).
    `portals/PortalEye` + mixin (TAIL of `Camera.setup`). `portals/PortalWarm` (portals to other dimensions; untested).
  - `mixin/AnimalTrimCacheMixin`, `mixin/VeilShaderEventMixin`, `mixin/MissingResourceMixin` (negative cache per
    `FallbackResourceManager`), `mixin/JourneyMapThemeMixin` / `JourneyMapMiniMapMixin` / `JourneyMapCacheMixin`
    (`JourneyMapReset.keepTheme()`: every reset of a server session but the first).
  - Review fixes (2026-11-22): `ColorwheelWarm.reset` empties a recycled draw manager's engine state (instancers,
    culling groups, draw lists) as its own delete() does; `MissingResourceMixin` records and answers only in play
    (render thread, a level, no overlay), because dynamic packs fill up during a reload; `LatePassengers.reseat` gives
    up when the player is more than 16 blocks from the vehicle.
  - Not done: the outer ring of the far side and its entities still load on arrival (about four frames of 0.1 to 0.2 s
    at a first crossing); video memory of 3 + 2 x 3 + 1 pipelines at a high resolution is not measured; KubeJS's local
    web server thread spins on `accept` (one core; `kubejs/config/web_server.json` `enabled`, the pack's file).
- `colorwheel/ColorwheelCut` + `mixin/ColorwheelCompileMixin` / `ColorwheelEngineMixin` / `ColorwheelEnvironmentMixin`
  (client; `colorwheel` + `immersive_portals_core`, the last two also `sable`): Immersive Portals' two cuts of Create's
  instanced parts, ported to Colorwheel (an optional client mod of the pack since 2026-11-14; without it Flywheel is off under Iris and the
  parts are block entity draws on the CPU). Immersive Portals patches Flywheel's `Compilation`, `EmbeddedEnvironment` and
  `EngineImpl`; Colorwheel has its own (`ClrwlCompilation`, its `EmbeddedEnvironment`, `ClrwlEngine`), so with Colorwheel
  parts behind a portal's far side showed in the portal view and a straddling ship's parts drew whole (log:
  "[IPStraddle] could not cut Flywheel parts"). The patch goes into the full vertex source at `ClrwlCompilation.compile`
  (vertex only: `gl_VertexID`; the fragment and geometry sources also have a `_clrwl_main`): `gl_ClipDistance[0]` from
  `iportal_ClippingEquation` and `[1]` from `ipa_ShipClipEquation`, the names Immersive Portals' `GlProgram.bind` hook
  already feeds (Colorwheel's programs extend Flywheel's `GlProgram`). Ship planes: `beginPass` after
  `ClrwlEngine.prepareFrame` reads each environment's plane from `IPStraddleRender.flywheelPlane` (far-half pass:
  `IPFlywheelStraddleClip.farHalf` by reflection, `flywheelFarHalfPlane` for that ship, reject the rest), as a uniform at
  `setupDraw` (instancing) and a buffer on shader storage binding 28 indexed by `matrixIndex` (indirect; Colorwheel uses
  0 to 9 and moves the shader pack's own up by 10, Immersive Portals' own uses 8). Train carriages: `mixin/ColorwheelCarriageMixin` at the head of Immersive
  Portals' `IPCreateCarriageCrossingClient.registerEmbedding` puts Colorwheel's environment into its `EMBEDDINGS` map
  (it keeps only Flywheel's type; the map is read by object, so its own model rebuilds and copy list then work), and
  `beginPass` reads the carriage's plane from its private `flywheelPlane(carriage, origin)`, and in a far-half pass
  from `farHalfPlane` with the crossing in the map its selection lambda holds (a field of the lambda object). Test (2026-11-14, hidden game on the NVIDIA card: `__NV_PRIME_RENDER_OFFLOAD=1
  __GLX_VENDOR_LIBRARY_NAME=nvidia`, else the headless desktop uses the built-in graphics): a ship is made with `/sable
  assemble area`, pushed on blue ice with `/sable physics impulse @l linear 0 0 30 global` until `/ip_aeronautics
  straddle status` says 1 session; `/sable teleport` into a portal makes no session. Results: shaft behind the portal
  gone (was sticking out 2.6 blocks), far half inside the portal as without Colorwheel, cogwheel behind the nether
  portal's plane gone from the portal view. A ship is not drawn at all for a player in the other dimension, with or
  without Colorwheel.
  Train test (same day): tracks (`create:track[shape=zo]`) up to the portal block on both sides link by themselves
  ("[IPCreatePortalTracks] linked the tracks"); a station by NBT (`create:track_station{TargetTrack:[I;-2,0,0],
  TargetDirection:1b}`), a `create:small_bogey[axis=z]` above the first track after the target, a block behind it and
  `create:controls[facing=south]` in front (a bogey attaches along its axis, not upward; no controls, no train). The
  station's `enterAssemblyMode(null)` and `assemble(uuid)` and the train's `speed` need code: a temporary server tick
  hook that reads a command file did it (not kept). Negative speed went toward the assembly direction. With the carriage
  in both dimensions: bogey and wheels behind the portal gone (they stuck out without the port), and the nether side
  shows the carriage coming out as without Colorwheel.
  macOS (2026-11-15, 0.32.2): `beginPass` bound the ship-plane buffer as `GL_SHADER_STORAGE_BUFFER` on every pass, also on
  the instancing backend. OpenGL 4.1 has no such target: GL_INVALID_ENUM each pass, which Immersive Portals' check logs
  with a stack trace ("[iPortal] OpenGL Error1280", `IrisPortalRenderer.doMainRenderings`; 4,350 in 7 minutes of play, a
  31 MB latest.log). `uploadPlanes` now runs only with OpenGL 4.3 or `ARB_shader_storage_buffer_object`
  ("[colorwheel cut] no shader storage buffers ..." once); the instancing shaders take the plane from the uniform. The
  patched vertex shaders compile on Apple's GLSL 4.10.
- `colorwheel/ColorwheelUnits` + `mixin/ColorwheelProgramMixin` (client; `colorwheel` + `iris`; compiles against Iris from
  Modrinth's Maven, version id `KduFYu4t` = 1.8.14-beta.1): a fault of Colorwheel 1.3.2 itself on macOS, not of our patch.
  `ClrwlProgram.getReservedTextureUnits` keeps units 0 to 6 for Flywheel (diffuse, overlay, lightmap, crumbling,
  instances, light LUT, light sections), 7 and 8 for its transparency (OIT) and one per coefficient rank of the pack
  (Complementary's `colorwheel.properties`: `3,3`, so 11 in all); Iris gives the shader pack's samplers the rest of
  `GL_MAX_TEXTURE_IMAGE_UNITS`, which is 16 on Apple. Colorwheel turns OIT off on Apple (`GlCompat.SUPPORTS_OIT`:
  renderer name starts with "apple") but keeps the units, so Complementary's translucent programs had 5:
  "IllegalStateException: No more available texture units while activating sampler shadowtex1", "Could not compile
  shader: clrwl_gbuffers_translucent/create_instance_fluid/...", fluid in glass pipes and glass on contraptions not
  drawn, and the chat lines "[Colorwheel] Current shader pack contains broken shaders, expect graphical issues" /
  "Disable this alert (click here)" (the "crash" report of 2026-11-14; no JVM crash was found). 0.32.2 gave back the
  OIT units only (9 free): enough for the overworld, but the End's translucent programs still failed at
  `shadowcolor0` (seen as soon as the join-time build compiled all three dimensions). 0.32.3: on a driver with no more
  than 16 units, the wrap around `ProgramSamplers.builder` in `postLink` keeps a shareable unit (crumbling, light LUT,
  light sections, OIT) only when the linked program has its sampler uniform, and the `addExternalSampler` calls for the
  others are skipped (Iris refuses an external sampler on a unit that is not reserved). Why that is safe: the light
  textures are buffer textures (their own binding point on a unit, no sampler state), the crumbling texture is bound
  right before its own programs and `bind` takes a shader pack's sampler object off that unit for a program that
  reads it, OIT textures are bound by each OIT step. Units 0, 1, 2 and 4 always stay: they are bound once per pass.
  With more than 16 units nothing changes (not one line of this runs on the 4090).
  Test: see `flywheel/LightBufferFix` below (same day, same scenes).
- `flywheel/LightBufferFix` + `mixin/FlywheelTextureBufferMixin` (0.32.3; client; `flywheel`, which Create carries): a fault of
  Flywheel 1.0.6 on a driver without Direct State Access (macOS), with or without us. `InstancedLight.bind` (Flywheel's
  instancing backend and Colorwheel's) attaches its two light buffers to buffer textures at the start of every pass
  (`TextureBuffer.bind`: `glTexBuffer`), but `InstancedLight.flush` fills them only when the light storage has
  sections (`capacity() != 0`). Without DSA a buffer is a `glGenBuffers` name that is no buffer object until its first
  bind, so `glTexBuffer` raised GL_INVALID_OPERATION once per pass. Nothing drew wrong; Immersive Portals reads the
  error flag each time it draws portals and logged "[iPortal] OpenGL Error1282" with a stack trace on every frame with a
  portal in view (4,182 in 2 minutes). The mixin binds a not yet existing buffer once before the attach (empty buffer,
  legal). Found with temporary `glGetError` probes (HEAD/RETURN and after-INVOKE injections into
  `ClrwlInstancedDrawManager.renderSolid`, `ClrwlProgram.bind`); the error sat between "after bindLightAndOverlay" and
  "after light.bind". Error 1280 hid it in 0.32.1: the flag holds one error per kind and Immersive Portals reads one.
  Count every "OpenGL Error" line after a change here, not one code.
  Test (2026-11-15, MacBook Pro M1 Pro, macOS 26.6.2, "OpenGL 4.1 Metal - 90.5", Complementary Unbound r5.9.3): a copy
  of the instance in its own Prism data folder (`open -n -g -a "Prism Launcher" --args -d <folder> -l <instance> -w
  <world> -o <name>`; `cp -Rc` clones on APFS), no pre-launch command. The game is driven without the desktop: a
  KubeJS server script in the copy reads `kubejs/hc_cmd.json` every 5 ticks and runs its commands when the `id`
  changes (`server.runCommand`; feedback lands in latest.log), with two extra words: a station's
  `enterAssemblyMode(null)` + `assemble(uuid)` and `Create.RAILWAYS.trains` `speed` (a script reload resets the last id:
  never put `kubejs reload` in a batch). Pictures of the game window alone: `screencapture -o -l <window id>` (the id
  from `CGWindowListCopyWindowInfo`), never the whole screen. Scenes: creative motor, cogwheel, shafts, a mechanical
  pump with glass fluid pipes between two water sources, a mechanical bearing turning stained glass and slime, a
  nether portal with a large cogwheel behind its nether plane and a small one beyond it; a 1x2x4 Sable ship (`/sable
  assemble area`, `/sable physics impulse @l linear 0 0 5 global` on blue ice, `/sable paused true` when `/ip_aeronautics
  straddle status` says 1 session); a train (tracks to the portal on both sides, station by NBT, small bogey, controls,
  speed -0.05 until the carriage stands in the portal plane). Results: as shipped (0.32.1) 4,340 "OpenGL Error1280"
  and the texture unit error; our five Colorwheel mixins off: no 1280, the texture unit error and 3,041 "OpenGL
  Error1282" (so those two are not our patch's); Colorwheel removed: none; 0.32.2: no "OpenGL Error" line of any code, water in
  the glass pipes and the contraption's glass drawn, the large cogwheel cut from the portal view, the ship's and the
  carriage's parts cut at the portal (nothing behind the frame, far half in the portal), one resource reload per
  launch as before. 0.32.3 (merged with the join-time program build): the same scenes again, plus the block-breaking
  overlay on a cogwheel, shafts and a pump (`ServerLevel.destroyBlockProgress`, one breaker id per block), 70 programs
  built at the join (42 + 14 + 14, 7.9 s, none dropped; 0.32.2's unit rule dropped two of the End's), menu to world 61
  to 65 s as before the merge. Other packs: Complementary Reimagined r5.9.3 as Unbound; BSL 10.1.1 and MakeUp
  UltraFast 9.5f have no Colorwheel programs (its "not compatible" chat line, Create parts drawn by Create); Photon
  1.3b does not load on a Mac at all (Iris's own composite program runs out of texture units, shaders go off).
  Not tested: an Intel Mac.
- `colorwheel/ColorwheelWarm` + `mixin/ColorwheelSourcesMixin` (client; `colorwheel`; compiles against Colorwheel from
  Modrinth's Maven, version id `lV874ox8` = 1.3.2): Colorwheel keeps a `ClrwlProgramSources` per Iris pipeline and rewrites
  the pack's program sources there on first use (`ClrwlTransformPatcher`, a glsl-transformer parse: 30 to 50 ms per
  program on the render thread). With portals that is every dimension and every portal-view pipeline, at the moment a
  portal comes into view: 0.3 to 0.7 s in Shane's game, three times in one session (2026-11-14; JFR showed the render
  thread in `ClrwlCompilation.compile` / `parseTranslationUnit` at the "Created pipeline data" log line). The wrap around
  `getGbuffersSources` keeps one result per (program set, program id, OIT mode, buffer offset, texture map) for all
  pipelines; `PipelinePrewarm.warmColorwheel` fills it for the three dimensions behind the loading screen (24 rewrites,
  about 0.5 s). Measured with a temporary HEAD/RETURN timing mixin on `ClrwlProgramSources`, `ClrwlCompilation` and
  `ClrwlCompilationHarness` (hidden game, RTX 4090, warm driver cache): at the first Create parts 138 -> 35 ms, at the
  portal's first view 40 -> 10 ms. That test was too small and only with a warm driver cache: in Shane's world the first
  session on 0.32.1 froze 3 to 5 s at the first look at a portal (three pipelines' programs in a row, 1.4 to 1.9 s each).
  The cost is the driver's link, per program: about 160 ms with the NVIDIA shader cache cold (any build that changes
  Colorwheel's shader text, as ColorwheelCut's does, makes it cold once), a few ms warm.
  0.32.2, programs: `mixin/ColorwheelProgramsMixin` (HEAD of `ClrwlCompilationHarness.compile`) records each
  `ClrwlShaderKey` Colorwheel builds to `haven/colorwheel-programs.txt` in the game folder, one line of shader part names
  and enum names. `ColorwheelWarm.warmEngines` (started by the Iris prewarm) waits, one check per frame, until
  Colorwheel has an engine (it is made at the level's first drawn frame; Flywheel's manager makes it in `lateInit`), then
  makes the nether and end client levels (`ClientWorldLoader.getWorld`), their managers and engines (inside
  `withSwitchedWorld`, because Flywheel only supports the current level, then the private `lateInit` by reflection), and
  builds every recorded key plus the six culling programs for every pipeline of every engine (`getPipelineData` by
  reflection). A key whose parts no loaded class names (holders listed in `HOLDERS`) is skipped. The list only grows.
  Loading screen: `portals/PrewarmScreen` + `mixin/PrewarmScreenMixin` (vanilla `ReceivingLevelScreen`). At a join with a
  shader pack on, the screen says "Compiling shaders..." (lang key `haven_compat.compiling_shaders`) and does not close
  until the round ends (`PrewarmScreen.done()`: after the Colorwheel program build, at once when Colorwheel is absent,
  on any failure). Limits: 20 s for the round to start, 180 s in all. Not held: shaders off, and later dimension
  changes (the flag resets only when Iris destroys its pipelines). Test (2026-11-15, hidden game, cache off): text from
  the first frame to the "[colorwheel warm] ... the_end" line, world within 3 s after it; a `tp` to the nether and a
  join with shaders off showed "Loading terrain..." and no delay.
  Test (2026-11-15, hidden game on the 4090, `__GL_SHADER_DISK_CACHE=0`, 24 kinds of Create blocks, a pulley
  contraption, a nether portal with a second portal behind it, 16 recorded keys): at the look 41 programs / 6.9 s
  without the list; with it 0 programs at the look and 48 + 16 + 16 programs / 17 s at the join. Cache on: 48 programs
  in 1.9 s at the join. Always test this with the cache off and a second launch; the player must face away at the join.
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
  Only `AeCableBus` and `AeBlockTurn` link against a mod here (`compileOnly` Create 6.0.10 slim and AE2 19.2.17, never
  shipped; `dyeing/` also links against Create); the others work on NBT keys and reflection (`MekanismData`: `writeSustainedData`; `RsData`:
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
- `tracks/WoodTracks` + `mixin/RailwaysSetupMixin` (both sides, `mixins` list; `railways`, `haven_backports`; compiles
  against Steam 'n' Rails from CurseForge's Maven and Create's Registrate): pale oak and poplar train tracks (0.34.0),
  three gauges each. Steam 'n' Rails 0.2.1 makes tracks for other mods' woods in `GenericTrackCompat`, which takes only
  the mods of its own `Mods` enum, so `WoodTracks` makes the same calls (`TrackCompatUtils.makeTrack`, the gauge maps of
  `CRTrackMaterials`, `CRBlocks.WIDE_GAUGE_TRACKS` / `NARROW_GAUGE_TRACKS`, `CRItems.ITEM_INCOMPLETE_TRACK`) at the TAIL
  of `ModSetup.register` (its Registrate still takes entries; the same thread). Ids as its own compat tracks:
  `railways:track_haven_backports_<wood>[_wide|_narrow]`, `railways:track_incomplete_...`. Steam 'n' Rails writes
  blockstates, models, names, tags, loot and recipes only in its data generation, so they are files here, made by
  `tools/wood_tracks.py` (its docstring has the command and the texture method: birch track textures, wood pixels moved
  to the new wood's planks colour); never hand-edit them. A material's models must not be read from the material inside
  `customModels` (it runs at once on a client, before `build()` returns: the first build crashed mod loading there).
  A new Haven Backports wood: add it to `WOODS` in both files and run the tool. A Steam 'n' Rails update: compare
  `GenericTrackCompat.register` and `TrackCompatUtils.makeTrack`. Test (2026-11-15, test server and hidden client): the
  six blocks place and render (straight, crossing, the portal piece), names and icons, `create:tracks`,
  `create:girdable_tracks`, `mineable/pickaxe`, each drops itself. Recipes through real machines (a depot, a deployer
  fed by a hopper, a mechanical press, a mechanical saw; a birch slab as the control): slab to standard track, slab to
  narrow, standard track to wide. Curves and trains through a temporary command (not committed) that calls Create's
  own paths as the test player (`TrackBlockItem.useOn` twice; the station item on the track, then on the ground;
  `StationBlockEntity.enterAssemblyMode`, `trackClicked` with a railway casing, `assemble`; then `manualTick` and
  `targetSpeed` for 200 ticks): each of the six tracks took a 90 degree curve (standard 8 blocks, wide 16, narrow 8,
  the same results as Steam 'n' Rails' birch tracks), got the bogey of its gauge, and its train (bogey, casing,
  controls, super glue) drove straight, curve, straight to the track end, not derailed; the curves render. Test notes:
  lay the track a few ticks before the station click (the track graph must exist), and a wide gauge curve of 12 fails.
- `repository/` + `mixin/RepositoryCatalogMixin`, `RepositoryTileMixin`, `RepositoryStorageMixin` (both sides, `mixins`
  list; `ars_nouveau`, `sophisticatedstorage`; compiles against both, Sophisticated Core and GeckoLib from Modrinth's
  Maven): the Repository Upgrade (`haven_compat:repository_upgrade`, 0.33.0), a Sophisticated Storage upgrade that makes
  a chest, barrel, limited barrel or shulker box an Ars Nouveau Repository. Ars Nouveau 5.13.3's Repository has no
  logic others use: the Repository Catalog joins the repositories that touch it into one inventory (`ControllerInv`)
  and knows them by `instanceof RepositoryBlock` and the `MAP_INV_CAP` capability; Wixies, Starbuncles and the Storage
  Lectern talk to the Catalog. So: the capability is registered for the four storage block entity types and answers
  only with the upgrade in (`StorageMapInventory`, `RepositoryTile`'s rules over the storage's own handler: item frame
  and scroll filters by position, read again every 20 ticks; stack upgrades and Sophisticated's filters hold);
  `RepositoryNetwork.build` replaces the Catalog's private `buildRepositoryNetwork` (HEAD, cancelled) and
  `RepositoryNetwork.changed` replaces `RepositoryTile.invalidateNetwork()`, both also passing through upgraded
  storages; a storage calls `changed` when its upgrade caches are invalidated and on load. One inventory enters once,
  at the block that holds it (`RepositoryUpgrade.key`: a double chest's main half; a linked storage group's id); the
  other block passes the search on and is watched. The item is in the `sophisticatedstorage:upgrade` tag only (not for
  backpacks), one per storage, no settings tab. Recipe: the Repository's pattern (gold nuggets, archwood logs) around
  an Upgrade Base. Icon: Sophisticated's plate with the swirl of `blue_archwood_log_2` in its ink colour.
  An Ars Nouveau update: compare `RepositoryCatalogTile.buildRepositoryNetwork`, `RepositoryTile.invalidateNetwork`
  and `IMapInventory` with 5.13.3. Test (2026-11-15, test server, a temporary command, not committed): Catalog, a
  double chest, a Repository behind it, barrels, a chest without the upgrade. Catalog slots 189 = 27 + 54 + 27 + 54 + 27
  (the double chest once, the plain chest out); 1,064 cobblestone in, 1,064 counted; extract by item; an item frame on
  the second half sent logs to the double chest, a frame on a barrel kept other items out; a Repository placed behind a
  barrel joined; the upgrade taken out or a half broken dropped exactly the members that lost contact; chunks
  unloaded and loaded again gave the same network. Client: icon, the storage screen with the upgrade in its slot, the
  recipe unlocks. Not tested: two endpoints of one linked storage group in one network (the key is from code only), a
  real Wixie or Starbuncle, the right-click on the front face (it is Sophisticated's `tryAddSingleUpgrade`; its three
  conditions were each checked).
- `copperchest/CopperLook` + `mixin/CopperLookChestMixin`, `CopperLookHolderMixin` (both sides, `mixins` list;
  `sophisticatedstorage`, `haven_backports`): Backports' Copper Chest as a look of Sophisticated Storage's chests. Why
  (Sophisticated Storage 1.6.2): a chest's look is a `WoodType` in its block entity and item component; textures come
  from `assets/<any>/storage_texture_definitions/<WoodType name>_chest.json` (the file path must equal the name, so
  ours are `haven_copper`, `haven_waxed_exposed_copper`, ...: no colon); `TierUpgrade.addTierUpgradeDefinition` is
  public and takes a block, so each of Backports' 8 copper chests gets the vanilla chest's definition with its look
  (reflection: the definition's getters sit on a private class). Mojang's copper chest textures have the vanilla
  chest layout, which Sophisticated's base layer uses too. The 8 wood types register in the block `RegisterEvent`
  (serial; `WoodType`'s map is not thread safe), without `Sheets.addWoodType` (no signs of them). Aging: the mixin
  makes the chest block randomly ticking and ports `ChangeOverTimeBlock`; random ticks need a player near (forced
  chunks alone do not age). Not done: a double Copper Chest whose halves differ in stage converts one half per
  click; Sophisticated's hinge sounds stay; no creative tab entry. Test (2026-11-14, hidden client on
  `just smoke --serve`): chests set by `setblock ...{woodType:"haven_copper"}` draw each stage under every tier;
  `randomTickSpeed 3000` with a player near aged one to oxidized in 6 s and left a waxed one; Create Deployers
  (`Inventory:[{Slot:0b,...}]`, `Mode:"USE"`, shaft on the side) waxed with honeycomb, took wax off and scraped with
  an axe, and upgraded a single and a double Copper Chest with items kept; Jade read "Waxed Exposed Copper Copper
  Chest". A person's own clicks and the item in an inventory were not tested.
  0.32.1: recipes (`data/haven_compat/recipe/haven_*_chest_from_copper_chest.json`, shapeless Backports chest + lever ->
  `sophisticatedstorage:chest` with the `sophisticatedstorage:wood_type` component; Sophisticated Storage's
  `storage_tier_upgrade` recipes copy the components, so tiers need none) and `mixin/CopperLookTabMixin` (TAIL of
  `WoodStorageBlockBase.addCreativeTabItems`, chests only: the eight looks for every tier; JEI reads that list). Seen in
  a hidden game: both recipes unlock by `/recipe give`, item icons draw in the hotbar in each stage's colour.
- `mixin/OxidationScrapeToolMixin` (both sides, `mixins` list; `createoxidized`): Create: Oxidation 1.0.0's
  `CreateOxidizedNeoForge.onRightClickBlock` scrapes and takes wax off only for `getItem() instanceof AxeItem`; a
  `@ModifyExpressionValue` on that `instanceof` also accepts `canPerformAction(AXE_SCRAPE | AXE_WAX_OFF)`, the question
  vanilla copper asks (Mekanism Tools' paxels are no AxeItem). Test (2026-11-19, `just smoke --serve` with this jar, the
  pinned one set to client side for the run): a powered Deployer in USE mode holding `mekanismtools:diamond_paxel`
  facing `create:copper_casing`: exposed -> unaffected; waxed weathered -> unwaxed weathered -> exposed; 3 damage.
- `oxidation/MoreOxidation` + `mixin/Oxidation*Mixin` (0.44.0, `createoxidized`): Create: Oxidation ages the copper
  blocks of other mods that Shane picked (`haven_compat/oxidation/blocks.txt`, 207 ids since 0.45.1: the 27 copper shafts and
  cogwheels in non-copper casings joined so their copper part ages, and the block tag
  `createoxidized:oxidizable_create_blocks`). Its properties go by block id, never by class (plain `Block` and shared
  brass/andesite classes): the id NeoForge's `RegisterEvent.register(key, id, supplier)` or Registrate's
  `Registration.register` is registering while the block is built. A `DeferredRegister` registers through the helper
  form, which these hooks do not see (More Beautiful Torches, Advanced Stands and Macaw's chain stayed out for that;
  Shane chose to leave them out, 2026-11-26); our own DeferredRegister blocks call `MoreOxidation.enter(id)` in their
  supplier. Client: the model swap for those ids and the aged textures this mod ships under
  `createoxidized:block/<stage>/compat/<mod>/` (`textures.txt`; made by `tools/oxidation/age_textures.py`, each one
  approved by Shane in the browser review). `report()` runs at common setup on both sides (`[more oxidation]`).
- `waystones/CreateWaystones` (0.44.0, both sides, `waystones`): Brass, Steel and Copper Waystones (Shane, 2026-11-26),
  Waystones' own `WaystoneBlock` (the mod's block entity type takes them through `BlockEntityTypeAddBlocksEvent`; every
  waystone is the generic `waystones:waystone` type), recipes like every waystone with the casing in the stone's place
  (Brass Casing, TFMG Steel Casing, Copper Casing; each recipe needs its mod), Waystones' tags and loot table, in the
  Waystones tab after Mud Bricks. Textures cut from the casings in the Fresh Waystones model shape (the pack's resource
  pack); item icons in Fresh's flat style. The copper one ages with Create: Oxidation; `CreateWaystoneBlock.updateShape`
  copies the stage and wax to the other half, so aging, an axe, a spray or honeycomb on either half changes both.
  Rig test: traintest `--oxidation` with `haven_compat:copper_waystone` first (pairs, the waystone block entity, every
  action with "both halves agree").
- `hud/HudPoseGuard` (client, any mods): the HUD frame's starting pose is recorded at `RenderGuiEvent.Pre` (highest); when a
  HUD layer (`RenderGuiLayerEvent.Pre`, lowest, not cancelled) or the open screen (`ScreenEvent.Render.Pre`, lowest) is about
  to draw from a moved pose (x, y, rotation, scale; z grows per layer on purpose and is kept), a pose of its own is pushed
  with the start's matrix and popped in the matching Post. Never pops another mod's pose (a first version popped down to
  the start and underflowed when a mod's own push was part of it: Chisels & Bits crashed on an empty stack). Why
  (2026-11-21): a Windows player's chat log and input line were drawn on JourneyMap's minimap at GUI scale 2 and off the
  screen at 3, gone with the minimap off; her JourneyMap log was not available, so the leak inside it is unknown (not
  reproduced here with her config, her HUD settings and active effects). Logged once per layer or screen (`[hud position]`,
  names the layer it happened after). Test (hidden client, `just smoke --serve`, throwaway mixin leaving a pushed and
  translated pose after `MiniMap.drawMap`): without the guard the chat log and input drew on the minimap (her screenshot);
  with it at the bottom left, no crash.
- `schematics/PartPrints` (both sides; `create`): Create's Schematicannon skips a block whose item is AIR. Create Ore
  Excavation's `multiblock`, `io_block` and `kinetic_input` (placed by the Drilling Machine's or Fluid Extractor's item)
  print for `ItemRequirement.NONE` (the core block still costs its item; a part printed alone falls away, it only stands
  when the block it faces is a machine part); `create:powered_shaft` prints for a shaft. Test (2026-11-22, `just smoke
  --serve` with this jar, a Schematicannon set up by commands with a 10x8x14 drill and steam engine schematic, skip missing
  on): the core and all 17 parts printed, all 9 engine shafts printed and stood as powered shafts.
- `megatorch/` (both sides; `torchmaster`, the map half also `journeymap`): Mega Torch areas on JourneyMap's fullscreen
  map. Server (`MegatorchAreas`): every 40 ticks each dimension with players reads Torchmaster's torch list once
  (`TorchmasterLights`: the private `lights` map of `FilteredLightManager`, by reflection; its public `getEntries()`
  returns an empty array in 21.1.13), and a player "knows" a torch once they stand within its radius horizontally
  (attachment `known_megatorches`, per dimension, kept on death; a broken torch is forgotten). Payloads: `megatorch_watch`
  (C2S, the map is open with the toggle on) and `megatorch_areas` (S2C, the known torches of the player's dimension and
  the radius), sent to a watcher only when that list changed. No chunk is read or loaded. Client: `MegatorchAreasClient`
  (toggle `config/haven_compat_megatorch_map.json`, off by default; the last list) and `MegatorchMap` (`@JourneyMapPlugin`,
  API 2.0.0, `compileOnly` from JourneyMap's maven): a theme toggle button subscribed on the first client tick so it sits
  under the other add-on buttons, one `PolygonOverlay` per merged shape (`java.awt.geom.Area` union, `PolygonHelper`),
  fullscreen only, rebuilt only when a list arrives. Test (2026-11-21, `just smoke --serve` with this jar, a hidden client
  copied from Haven, a throwaway KubeJS client script opening the map and clicking the toggle through
  `Screen.mouseClicked`): three torches by `setblock`, two within reach of the player and one 300 blocks away; off by
  default, no outline; on: one outline from the two known torches, the far one absent; relaunch: the choice is kept and the
  outline shows at once.
- `trees/HangingLogs` (pure, unit tested) + `mixin/ArsTreeCutMixin` (server, `mixins` list; `create`, `ars_nouveau`): a
  Mechanical Saw takes the whole Ars tree. Why (Create 6.0.10, Ars Nouveau 5.13.3): `TreeCutter.findTree` follows logs
  sideways and up only; Ars' `MagicTrunkPlacer.addBranch` ends each branch with a log one block under the branch tip,
  which stayed in the air with its leaves (8 single logs above a tree farm, 2026-11-13). The mixin adds, before the leaf
  search, each log of an `ars_*` mod right under a found log, above the cut level, with no log under it. Test
  (2026-11-13): `just smoke --serve` with the jar, `place feature ars_nouveau:vexing_tree` and `cascading_tree`, three
  base logs removed, a powered saw on the fourth: no log left above the base level.
- `schematics/PrintOwner` + `mixin/CannonPrintStarterMixin` + `mixin/CannonDeployerOwnerMixin` (server, `mixins` list;
  `create`): a Deployer printed by a Schematicannon takes the player who started the print as its owner (any
  `DeployerBlock`: Create Encased's cased deployers, `createcasing:brass_deployer` and others, extend it and use
  Create's `DeployerBlockEntity`; up to 0.30.12 only the block id `create:deployer` matched, so printed brass deployers
  had no `Owner`, live 2026-11-14). Why (Create
  6.0.10): `DeployerBlockEntity.owner` (NBT `Owner`, the id of its `DeployerFakePlayer`) is set only by
  `DeployerBlock.setPlacedBy` from a `ServerPlayer`; the cannon copies `writeSafe` data (the mode) and
  `BlockHelper.placeSchematicBlock` passes no placer, so the print acted as the ownerless "Deployer" and FTB Chunks
  (`def_fake_player_ids` on, `def_fake_players` off) refused it in claims (2026-11-13; Create issue 6219). The cannon
  stores no player (`SchematicannonBlock.setPlacedBy` keeps a yaw only), so the starter is whoever last sends
  `ConfigureSchematicannonPacket` `PLAY` or `PAUSE` through the cannon's menu (only those two leave `STOPPED`; `PAUSE`
  counts because a paused cannon resumes by itself after a missing item, an unloaded target or empty fuel): kept on the cannon as
  `HavenPrintStarter` (server save only, not in client packets), written as `Owner` into the data of each deployer in
  `launchBlock` (so a block in flight keeps it across a restart). The schematic file's own `Owner` is never used: a
  player can upload a file with any id. Not covered: creative instant placement (`SchematicPlacePacket`), a print
  begun before 0.30.10 until Play is pressed again, deployers already printed. Test (2026-11-13, a throwaway harness
  class in a dev server with Create 6.0.10, two starts): start 1 pressed Play as a fake `ServerPlayer` holding the
  cannon's menu; start 2 (after the restart) launched a deployer whose schematic copy had another `Owner`: the printed
  one's `Owner` and fake player id = the starter; one from a cannon nobody started: no `Owner`, id `9e2faded-...`
  (Create's fallback); a chest printed beside it: no `Owner`. Not tested with FTB Chunks.
  Every other printed block (0.30.13, `mixin/SchematicPlacerMixin` + `PrintOwner.placer`): Create already calls
  `Block.setPlacedBy(level, pos, state, null, stack)` for each block it lands (`BlockHelper.placeSchematicBlock`, after
  the block entity data is loaded, exceptions swallowed). While a cannon lands its flying blocks (`tickFlyingBlocks`
  HEAD/RETURN set a thread-local starter) that null becomes a NeoForge `FakePlayer` with the starter's id and name
  (online player, else the server's profile cache; unknown: no placer), so blocks that take their owner in
  `setPlacedBy` get it. Never the real `ServerPlayer`: a scan of every pack jar's `setPlacedBy` (309 overrides) found
  blocks that read the placer's hands (Create and Copycats+ copycats, Storage Drawers: the off-hand item), open a
  screen or send packets (Waystones, Refurbished Furniture door mat and mailbox, FTB Quests task screen) or use the
  position (MCreator blocks, MineColonies graves). Kept with no placer (`NOT_PLACED`, by block namespace): `waystones`
  (would activate the waystone for the placer), `minecolonies`, `create_furnitures`, `create_sa`. Owner readers found:
  Create deployers (`ServerPlayer`), Mekanism (`ISecurityTile` owner when unset, UUID), SecurityCraft
  (`OwnershipEvent`, name + UUID), Refined Storage (`PlayerAwareBlockEntity`, profile id), AE2 (`IOwnerAwareBlockEntity.
  setOwner(Player)`), FramedBlocks one-way window, Easy Villagers converter, FTB Quests barrier, Deep Seas mine, Iron's
  portal frame, KubeJS blocks (UUID). No owner in `setPlacedBy`: Sophisticated Storage, Create New Age, FTB Chunks
  (claims are per chunk), MineColonies huts. Create's `AdvancementBehaviour` skips fake players (no advancement
  credit, as before). Not covered: creative instant placement, belts (`ForBelt`), AE2 (see the test).
  Test (2026-11-14, `just smoke --serve` with this jar, a throwaway harness mod: `launchBlock` on a cannon whose
  starter was set, the starter online and offline (profile cache only), same results): `create:deployer`,
  `createcasing:brass_deployer`, `createcasing:copper_deployer` (schematic data with another `Owner`): `Owner` =
  starter (0.30.12: plain yes, brass none, copper the schematic's); `mekanism:enrichment_chamber`: security owner =
  starter (0.30.12 none); `securitycraft:keypad_chest`: owner name and id = starter (0.30.12 placeholder); `ae2:drive`:
  still no owner (`proxy.p` -1: AE2 gives a fake player no id); `refinedstorage:grid` and the mailbox: no owner data in
  the block entity either way; oak door and red bed (both halves printed): two halves, nothing dropped; waystone,
  chest, cogwheel, copycat: as with 0.30.12. Not tested: a real print from a schematic file, FTB Chunks.
- `defaults/LoadDefaults` writes options.txt as text (`OptionsFile`), never `Options.save()`: at
  `Options.loadSelectedResourcePacks` only vanilla key mappings exist, and a save there dropped every mod key binding.
- `PackDefaults`: the pack's `config/haven-pack-defaults.json` (`file()`, `section(file, key)`, `root(file)`); each fix
  reads its own key. `Os.macOS()`: the OS check (`os.name`, or `-Dhaven_compat.assumeMac=true`).
- `defaults/`: the pack's one-time defaults (from haven-create-ui-ux up to 1.3.0). `DefaultsState`: `config/
  haven-pack-defaults.state.json` (keys `seen`, `shaderApplied`, `shaderOptions`, `ordered`, `darkLoadingScreen`, `keys`). `LoadDefaults`
  (`mixin/LoadDefaultsMixin`, TAIL of `Options.loadSelectedResourcePacks`, once; before the first resource load, so no
  reload): `resourcePacks` enabled once in order (`PackList` pure, unit tested), `resourcePackOrder` rules once each (state
  `ordered`, `PackOrder` pure, unit tested), `darkLoadingScreen` sets `darkMojangStudiosBackground` once, `keys` rules move a key line once from its old default
  (`KeyDefaults` pure, unit tested; a key the player bound stays; mod keys read options.txt later in the same start; 0.40.0:
  rules chain in list order, a rule sees the value an earlier rule wrote in the same run, so G -> ' then ' -> Alt+7 ends on
  Alt+7 for a player still on G; values with a modifier (`key.keyboard.7:ALT`) compare as whole strings); `KeymapImport`
  (0.40.0, pure `parse` unit tested): a `haven-keys.txt` in the game folder (lines as options.txt stores keys, `key_<id>:<value>`;
  blank, `#` and non-`key_` lines skipped) is applied after the key moves, with or without a pack defaults file, as text
  like the rest (only those key lines replaced or added), then renamed `haven-keys.applied.txt` (an older one replaced);
  `[keymap import] applied N keys`; the pack lists
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
- `mixin/PortalStagnateMessageMixin` (client; `immersive_portals_core`): MixinExtras `@WrapWithCondition` skips both
  `Gui.setOverlayMessage` calls in `CollisionHelper.updateClientStagnateStatus` (the "not yet loaded" text and its clear;
  no IP option for it). The movement hold stays. Test: the mixin applies (`latest.log`, no `Mixin apply` failure).
- `mixin/PortalDepthCopyMixin`: `@Inject(HEAD, cancellable)` on `IPIrisHelper.newCopyDepthStencil` and `copyColor`.
- `updates/UpdateNags`: the update-popup fix's log and the Create Deep Seas target class name; `mixin/DeepSeasUpdateCheckMixin`
  cancels `UpdateChecker.check()` at HEAD (no Modrinth request, `updateAvailable` stays false, `DeepSeasUpdateScreen`
  never opens).
- `mekanism/ChunkWatchers` + `mixin/MekanismChunkTrackingMixin` (the only server mixin, in the config's `mixins` list; the
  `@Mod` class is `dist = CLIENT`, so a dedicated server builds no class of ours but applies this mixin):
  `PacketUtils.hasPlayersTracking` answers `!chunkMap.getPlayers(chunk, false).isEmpty()` (Immersive Portals' tracking).
  Test: a Mekanism fluid tank with fluid shows it (single player applies it through the built-in server).
- `mekanism/ChemicalGlow` + `mixin/MekanismChemicalGlowMixin` (client; `mekanism`, `iris`): `@Redirect` of
  `RenderData.calculateGlowLight` in `MekanismTileEntityRenderer.renderObject(RenderData, BlockPos, Model3D, PoseStack,
  VertexConsumer, int, float)` (receiver `@Coerce Object`, Mekanism is not on the classpath): a `ChemicalRenderData` gets
  `LightTexture.FULL_BRIGHT` while `IrisApi.isShaderPackInUse()` (method handles, resolved once), else Mekanism's own
  answer. Mekanism pins multiblock contents at full sky light, which vanilla draws at full brightness anywhere;
  Complementary lights block entities from the scene, so a gas (alpha 0.2 + fill fraction) in a roofed room was drawn but
  not visible (2026-10-27, measured in the pack's haven-chemical-shader report). Test: a roofed Dynamic Tank a third
  full of hydrogen with Complementary on shows a lit haze; fluids and the no-shaders look unchanged.
- `portals/RefusedCrossing` + `CrossingBack` (pure math, unit test) + `mixin/RefusedCrossingMixin` (server, `mixins` list):
  `@WrapOperation` on `teleportEntityGeneral` in IP's `onPlayerTeleportedInClient`, `@Local` args and the `failReason`
  String; the portal comes from the `portalId` argument, its plane from public `getOriginPos()`/`getNormal()` (reflection).
  The eye moves along the normal to 0.1 in front (front = the side the normal points to: IP teleports on front -> back).
  The packet's "eye before teleportation" is the eye at the end of the crossing tick, already behind the plane: never use it
  as the way back. Only for the reason text "has awaiting teleport" (an upstream rename turns the fix off).
  Test (2026-10-21): a throwaway server mod held the client's confirmation and set the server position 2 cm past the
  plane on the crossing request, then nudged the player 0.15 back; without the fix the nudge crossed the reverse portal.
- `portals/MountCrossing` + `MountCorrection` (pure rule, unit test) + `mixin/MountCrossingMixin` (client, `client` list;
  `immersive_portals_core`; registered from `LatePassengers.register`): wrong-side exits while mounted. Immersive Portals
  never refuses a crossing for a rider (`validatePlayerTeleportationAndGetReason` returns early for `getVehicle() != null`),
  so `RefusedCrossing` does not cover it. The cause is vanilla: the server often rejects the vehicle move that carries a
  mount through the plane ("<mount> (vehicle of <player>) moved wrongly!", or the silent collision check). It then sends
  `ClientboundMoveVehiclePacket` with the old position. That packet has no dimension, and it arrives after the client
  has already crossed, so vanilla's handler gave the mount in the Nether its Overworld coordinates. Then the rider hung at
  the plane, or the next server correction ("moved too quickly! -734,-26,1449") carried the rider back over it. IP took
  that as a crossing of the reverse portal, in a ping-pong to "Combo teleport out of limit", and the player stayed on the
  wrong side (a drekki rider, client log 2026-11-24 03:25). The mixin (HEAD of the packet's `handle`, client thread)
  drops a correction that points to within 8 blocks of the mount's spot before a dimension change or a jump of more than
  3 blocks in the last 3 s, if that correction is for the other dimension or nearer the old spot than the mount's
  current spot. Log: `[mount crossing] a correction of <mount> to <old dim> ... came after its crossing ...: dropped`.
  Test (2026-11-24, copy of the traintest rig at `~/.cache/traintest-portal`, own container): the throwaway mod
  `portalprobe` adds 60 ms each way on the player's connection (a Netty handler first in the server pipeline), puts a
  tamed horse (or a Mature drekki by reflection) 6 blocks before the rig's nether portal, mounts the player, and holds W.
  The legs go Overworld -> Nether and back. The client logs every frame near the plane and every move-vehicle packet.
  Control (0.39.0): 4 of 4 westward crossings got the stale Overworld correction; at local ping one left the horse at
  Nether x -835 with its rider stuck at the plane. Fix: 8 of 8 legs OK, rider and mount together, 4 corrections dropped.
  0.41.1: only the latest crossing counts, at most 6 drops per crossing (`MountCorrection.stale`, `MAX_DROPS`). 0.41.0
  kept every crossing spot for 3 s, so after a run back through the portal the server's real correction lay near the
  spot of the crossing before and was dropped, 20 in a row: the server refused the drekki's moves while the client ran
  on, then pulled the rider back to the portal (live, 2026-11-24 14:33). Test: the traintest rig's `trainprobe-drekki`
  flag (`probe/DrekkiRun.java`: a roster-registered Mature drekki at the train-track portal, 60 ms each way, turns 6
  ticks after each arrival, every 4th arrival runs out 3 s): 0.41.0 dropped same-dimension corrections, 0.41.1 none,
  stale other-dimension drops kept (7), 20 legs on the drekki.
- `portals/CarriedRiders` + `mixin/CarriedRidersMixin` (server, `mixins` list; `create` + `immersive_portals_core`; on
  `Carriage$DimensionalCarriageEntity`: RETURN of `dismountPlayer`, HEAD of `updatePassengerLoadout`): a train passenger
  whose seat crosses a portal before the carriage has a copy on the far side was left standing on the old side, never
  seated (gooplife, 2026-11-24). Such a player (filed under "PlayerPassenger", not teleported) is remembered 5 s; when a
  copy in another dimension loads its passengers with that player filed and the player is within 4 blocks of the spot,
  the player goes through with IP's `IPCreateRiderTeleport.teleport` (vanilla's when that fails) and Create seats them in
  the same call. Log: `[carried riders]`.
- `portals/SeatsAtPortals` + `mixin/SeatsAtPortalsMixin` (server, `mixins` list; `create` + `immersive_portals_core`;
  HEAD of `SeatMovementBehaviour.visitNewPosition`, cancellable): Create ejects a seated rider when the seat moves into a
  block that occludes (or a bottom slab), and a portal set in a wall has those right behind its plane. On a carriage the
  eject is skipped when the block is past the crossing's plane (`IPCreateCarriageCrossing.of`, `Crossing.isBeyond`) or
  within 4 blocks of an IP `Portal` entity (the crossing is often not set up yet when the front seat meets the wall);
  IP's hand-over then moves the rider. Log: `[portal seats] ... rider kept for the hand-over`.
  Test for both (2026-11-25, traintest rig, `run_pair.py`: Tester drives, Tester2 on the front seat, the drill portal
  at x -1088, z 1274 both ways): before, both runs (pair-hints-on-2, trip-seats) logged "P2 PASSENGER LOST" with the
  dismount stack in `SeatMovementBehaviour.visitNewPosition`; after, 4 runs (trip-seats2, seat42-1..3 with 0.42.0 on
  all three sides), 0 lost, Tester2 through all 4 portals with the driver each run. `CarriedRiders` fired in none of them
  (the seat fix kept the rider first): it is the fallback for a rider who still gets off before the far copy exists, and
  has not run in a real crossing yet. With 50 ms each way on both players' connections (the probe's `Latency`, run_pair
  `--latency 50`): 0.41.1 lost the passenger at the drill return again (rider-lat-1), 0.42.0 lost none (rider-lat-fix-1).
- 0.45.0, train riders at portals (Shane's two-carriage L&S Courier Line with a drekki conductor, 2026-11-26: on a rear
  seat he was put down at the Overworld side of the -835 portal and stayed there; "[IPTrainRider] moved ... (left
  carriage ...)" and no seat). Three changes, all server:
  - `CarriedRiders` also files every player Create's `dismountPlayer(..., true)` hands over (train, carriage index, seat);
    a server tick seats one still not riding 100 ms later on that seat of the carriage's copy in its own dimension (free
    seat, within 16 blocks: the train runs on meanwhile). Log `[carried riders] ... seated again`.
  - `CarriedRiders.handOver` (RETURN of `updatePassengerLoadout`): when a copy filed a mob rider, the copies in other
    dimensions load their passengers at once (as Immersive Portals does for players). Gain is small (watcher saw the
    conductor 0.26 s -> 0.2 s after the empty carriage); the rest is Create's cutoff.
  - `SeatsAtPortals.notInWall` + `mixin/PortalWallRidersMixin` (HEAD of `Entity.isInWall`): a contraption rider within 4
    blocks of a portal is not "in a wall". Kept on its seat by the seat fix, the rider's head passed through the blocks
    behind the plane and took suffocation damage on most crossings; a train stopped across the drill portal killed the
    rider in that seat.
  Test: traintest `--courier --conductor` (probe `CourierBuild`: the rig train taken apart with `Train.disassemble`, the
  Courier built at the home station, front bogey on the marker, a conductor chicken or `--drekkicond` an employed drekki,
  a "Drill Probe" station placed at the drill line's end, Tester watching there) at 100 ms each way. A forced drop that
  also deletes Create's seat record (the live failure) was seated again in 100 to 142 ms (courier-conductor-fix-2,
  render-1, drekki-1); 0 suffocation hits against two chicken deaths before; Tester2 through every portal seated.
- `contraptions/ElevatorFloors` (0.45.0, server; `create`; NeoForge `PlayerEvent.StartTracking`): a player who starts
  seeing an elevator cabin gets its floor list (Create's `ElevatorFloorListPacket`). Create sends floors only when a
  game's cabin attaches to the pulley or when the list changes, so a cabin that came into view later held none and its
  Contraption Controls said "No Floors" (Shane's elevator at -665, 1660, twice on 2026-11-26). Test: traintest
  `--elevator`, Tester2 sent 1000 blocks away and back at the bottom: 0.44.0 came back with 0 floors and the cabin then
  left without Tester2 (elevator-floors-control-2, elevator-watch-1); 0.45.0 sent the 2 floors at once and Tester2 rode
  up (elevator-watch-fix). Log `[elevator floors]`.
- `carryon/CarrySlot` + `mixin/CarrySlotMixin` (0.45.0, client; `carryon`; redirect of the `Inventory.selected` write in
  `ClientboundSyncCarryDataPacket.handle`): Carry On sets the hotbar selection from its carry data on every sync, and
  syncs on every dimension change: after a portal the selection jumped to slot 0 (Shane, 2026-11-26). The write is kept
  only while the carry data says the player carries something (the slot lock Carry On means it for). Found with a JDWP
  field watch on `Inventory.selected` in the traintest client (`--jvm "-agentlib:jdwp=..."`; arm the watch only once
  the walk starts, from the start it slowed the game past the runner's timeout). Test: traintest `--slots` (probe
  `ProbeClient.slots`: a new slot every 10 ticks during the walk, "SLOT JUMP" when it changes otherwise): 6 jumps in 13
  crossings before, 0 in 11 after (slots-1, slots-fix-1). Log `[carry slot]`.
- `oxidation/AgedParts` + `mixin/AgedPartsStorageMixin` + `mixin/AgedPartsModelsMixin` (0.45.0, client; `createoxidized`,
  `create`): moving parts are Flywheel partial models picked by name (Create Casing's copper shaft and cogwheels:
  `EncasedPartialModels` maps), so Create: Oxidation's block model swap never reached them: they aged, waxed and scraped
  but looked new (Shane, 2026-11-27). While Flywheel builds a block entity's visual (`BlockEntityStorage.createRaw`; it
  builds it again on every block state change) the state is kept, and `Models.partial` takes the aged copy of a partial
  listed in `haven_compat/oxidation/partials.txt`. Copies: `haven_compat:block/oxidation/<stage>/<ns>/<path>`, made by
  `tools/oxidation/aged_partials.py <extracted assets> src/main/resources` (aged textures from `textures.txt`). A new
  copper block in `blocks.txt` with a spinning part: add its partial there. 0.45.1 added the copper drill head and
  roller frame. Still fresh (no aged texture yet): the copper mixer head, chain conveyor and belt cover. Log
  `[aged parts]`. Test: traintest `--oxidation` with the three Create Casing ids (oxi-shafts-3), encased ones (oxi-encased-1).
- `contraptions/SeatDraw` + `mixin/SeatDrawMixin` (client; `create`; `@WrapMethod` on `EntityRenderDispatcher.render`, so
  every pass: world, Iris shadow, Immersive Portals views): other players saw a seated rider on a train drift behind its
  seat and snap back (live server, 2026-11-25; the position was right, only the drawing). Vanilla draws the carriage at
  lerp(carriage xOld, x) and the rider at lerp(rider xOld, x); the rider's history goes stale or a tick behind (after a
  crossing, and a passenger's own `tickCount` stands still across game ticks), so it lagged and popped up to 16 blocks in
  one frame. A rider of an `OrientedContraptionEntity` (trains, minecart contraptions) other than the camera entity is
  drawn at the carriage's drawn position plus its seat offset (rider minus carriage), sampled once per game tick and
  lerped between the last two ticks; a change over 1 block in one tick (another seat, a teleport) is drawn at once. Only
  the drawn position changes. Not for bearing or other rotating contraptions (their riders turn about another point).
  Two earlier versions failed: this tick's offset turned by yaw only (stepped on ramps), and sampling on the rider's
  `tickCount` (2-block flips). Log: `[seat draw]`. Test (traintest `run_pair.py`, the driver turned to Tester2 once per
  seating and per crossing, the probe's `RiderView` logs `SeatDraw.shift` in rider.csv `shx..shz`; drawn rider minus drawn
  carriage per frame while moving): before, localhost 20 straight / 39 curve frames over 0.5 block (max 8.8), on a
  Bisect-like line (`--latency 50,30,5000,250`) 26 / 78 (max 16.6); after, 3 runs (2 on that line, 1 localhost) 0-1 / 1-2
  (max 0.77), 0 passengers lost.
- `contraptions/FloorDraw` (client; `create`; called from the same `SeatDrawMixin` hook when `SeatDraw` gives nothing):
  other players saw a player on a Create elevator duck and stand up over and over (live server, 2026-11-25). The cabin
  moves smoothly in each game, but the other player's position arrives late and uneven, so on the way up the floor rose
  past the drawn feet and the model sank into it, then popped up; down, the lag holds them above the floor where the cabin
  carries them. Create's own guard (`ContraptionCollider.saveRemotePlayerFromClipping`) searches only a few blocks. Another
  player (not riding, not the camera) whom the server reports on the ground (`ContraptionGround` keeps that true on a
  moving contraption), inside a moving translating contraption (pulley, piston, gantry; not trains or carts), is drawn on
  the lift floor nearest the drawn feet (a block top with two clear blocks above, searched 12 blocks up and down) at the
  lift's drawn height; at most 12 blocks of correction. Log: `[lift floors]`. Test (traintest `run_pair.py --elevator`
  with `server/trainprobe-elevator-ride`, the watcher in the cabin turned to Tester2 once; `RiderView.elevatorDrawn` adds
  `FloorDraw.shift` to the drawn gap): before, on `--latency 50,30,5000,250` the up ride drew Tester2 down to 11.3 blocks
  under the floor (82 jumps over 0.15 a frame), the server had it on the floor; after (a 4-block search first still sank
  to 8.8: the floor was out of reach), 3 runs (2 on that line, 1 localhost) gap -2.000 in every moving frame, 0 jumps.
- `mixin/RailsPhantomSpriteMixin` (client; `railways`; `@WrapOperation` on the `railways$uploadFrame` call in
  `PhantomSpriteManager.renderTick`): Steam 'n' Rails 0.2.1 re-uploads every phantom track sprite it holds a weak
  reference to when phantom visibility changes; a sprite of an atlas a resource reload replaced reads mip levels it never
  had ("Index 1 out of bounds for length 1" in `SpriteContents.upload`), a crash at game start (traintest rig, 2 of about
  150 starts, 2026-11-25, with hotbar 1.6.1 and 1.7.0). That upload is skipped and logged once (`[phantom tracks]`); not
  reproducible on demand, so the test is that the mixin applies (no `Mixin apply` failure) and phantom tracks still show.
- `mixin/VeilIrisBindMixin` + `VeilIrisClearMixin` (client; `veil` + `iris`; 0.42.1): a black box fixed over the middle
  of the screen with Complementary Unbound (a player on an RTX 5080, driver 610.88, 2026-11-25: gone with Unbound's
  Real-Time Shadows OFF or with Reimagined; bigger in survival than in creative; never seen on the rig's AMD card).
  Veil 4.3.2 (jar-in-jar of Sable 2.0.6) binds a plain framebuffer on Iris's colortex0 main texture for every Veil
  shader it applies (`ShaderWrapperMixin` -> its merged `IrisRenderingPipeline.veil$bindSimpleFramebuffer`), in the
  shadow pass and in GUIs too, and never unbinds it (its `@Share` flag does not carry from `apply` to `clear`). The bind
  mixin (priority 1500, after Veil's merge) replaces that method: outside the shadow pass and only while
  `shouldOverrideShaders()`, Iris's own `bindDefault()` (the colortex0 copy a flipping pack draws to); else nothing. The
  clear mixin binds the draw and read framebuffers of before `apply` again at the end of `clear`. The same fix as Veil's
  open pull 213 (FoundryMC/Veil, not in 4.5.2); drop these when the pack's Sable bundles a Veil with it. Related upstream
  report: Iris issue 3164 (Sable objects drawn wrong with shadows on). Test: the rig trip with Unbound (no `Mixin apply`
  failure, `[veil shadows]` in latest.log, normal screenshots); the box itself only on the player's NVIDIA card.
- `mixin/ShadowPassScreenGuardMixin` (client; `iris`; 0.43.1; HEAD of `RenderTarget.bindWrite`): 0.43.0 left the
  Unbound box on the player's RTX 5080, now behind the minimap (gone with Real-Time Shadows OFF). Veil's
  `AdvancedFbo.unbind()` is `getMainRenderTarget().bindWrite(true)` (screen image plus screen viewport); callers include
  `VeilFirstPersonRenderer`, `PipelineLevelRendererMixin`, `VeilLevelPerspectiveRenderer`, `FramebufferStack`. During
  the shadow pass (`ShadowRenderer.ACTIVE`) a `bindWrite` on the main target is refused unless the first caller outside
  `RenderTarget` is Iris (Iris binds the main target near the end of `renderShadows` before clearing `ACTIVE`); each
  refused caller is logged once (`[shadow pass] refused ... asked by <class>`): ask the player for that line. The rig
  (RTX 4090, not AMD: an earlier note here and in chat said AMD wrongly) never refuses anything and never shows the box.
- `mixin/ZeroMoveCollideMixin` (`immersive_portals_core`; HEAD of `Entity.collide`, priority 500, before IP's own hook):
  a zero movement returns at once. A seated player runs `move` with a zero vector every tick, and IP's portal-aware
  collision (`PortalCollisionHandler.handleOtherSideMove`) next to a train portal in a wall took over 100 ms of render
  thread on it (traintest JFR stall stack, 2026-11-24). Collision only shortens a movement: same result, no work.
- `mixin/FancyMenuScaleMixin` (client, `@Pseudo` on `de.keksuccino.fancymenu.util.rendering.RenderScaleUtil`;
  `fancymenu`): FancyMenu 3.9 sets a ThreadLocal (with a boxed Float) on every `PoseStack` push, pop and scale in the
  game; on the render thread the value lives in a plain static field instead, other threads keep the ThreadLocal. 111
  render-thread samples (about 2.2 s) on one traintest trip before. Same values; recheck the target names after a
  FancyMenu update (the mixin is `@Pseudo`: a rename turns it off without an error).
- `portals/PortalPipeModel` + `mixin/PortalPipesTfmgMixin` (client, priority 1500; needs `immersive_portals_core`, `tfmg`,
  `create`): TFMG `@Overwrite`s Create's `PipeAttachmentModel.gatherModelData` and redefines `getQuads`, and Mixin refuses
  IP's injections into methods merged at the same priority (1000), so IP 2.4.15's `MixinPipeAttachmentModel_PortalPipes`
  fails as a whole. Ours repeats its three hooks (rim attachment wrap, portal faces at RETURN, filler quads at RETURN) and
  calls IP's public `IPCreatePortalPipeModel` through method handles; it does nothing when `Mixins.getMixinsForClass` lists
  IP's mixin (a failed one still leaves its `ip$...` handler methods merged, so their presence proves nothing). Test: a fluid pipe ending at a portal shows a connection
  piece reaching the plane, not a rim (screenshots 2026-10-21). TFMG Community Edition (1.3.x, the pack's TFMG since
  2026-11-18) has no `PipeAttachmentModelMixin`: IP's own mixin applies again, and `CompatMixinPlugin` leaves ours out
  when TFMG's jar lacks `com/drmangotea/tfmg/mixin/PipeAttachmentModelMixin.class` (no `[portal pipes]` line then).
- `mixin/NtglProjectileSyncMixin` (client; `ntgl`): NTGL 3.2.0 makes `NetworkProjectileManager` only in the server's
  `AddReloadListenerEvent`, so `ClientPlayHandler.handleUpdateProjectile` hit null on every dedicated-server join. HEAD
  inject without target args (no NTGL types on the classpath), `Class.forName` + `get()` by reflection, cancel when null.
  `getConfig` has no callers, so the client loses nothing. Test: no "NetworkProjectileManager" NPE in a client log on join.
- `trash/TrashSlotLock` + `TrashSpot` + `mixin/TrashSlotVisibleMixin` / `TrashSlotToggleMixin` (client; `trashslot`):
  RETURN of `TrashSlotSaveState.getSettings(screen, @Coerce layout)`: the returned `ContainerSettings` is held shown
  on a screen with the player's hotbar (a menu slot of `Inventory`, index 0-8, active, near the box) and held hidden
  on one without (Train Schedule, Waystones, Elevator, AE2 Network Status: nothing to drag in); kept in a weak identity
  map; HEAD of `ContainerSettings.setEnabled` cancels the other value (TrashSlot's toggle key flips that flag, then
  `save()`s), so the toggle key does nothing. Spot: TrashSlot's default is under the bottom-right corner of
  `imageWidth` x `imageHeight`, which floats on screens whose box is bigger than the drawn inventory panel (Create
  filters, Card Binder, Beacon, Mekanism, ...). `TrashSpot.underHotbar` puts it under the hotbar panel's corner
  (last hotbar slot + 16 + 8 px; pure math `spot`/`close` unit tested: a vanilla screen gives TrashSlot's own default);
  within 2 px of the default the default stays (vanilla chests, AE2: there TrashSlot draws the slot as a tab of the
  box). Pack key `trashSlot.positions` (id -> `[x, y]`, TrashSlot's units, ids = keys of `TrashSlotSaveState.json`)
  wins, unused so far. Applied only while the settings hold the layout's `getDefaultSlotX/Y` (a dragged slot stays).
  Test 2026-11-11: a throwaway proof step placed and right-clicked every block with a block entity (5,854 jobs,
  click 4 ticks after placing, or SecurityCraft / Ad Astra / Sophisticated Storage kick the client), plus item,
  entity and inventory screens: 184 screen ids; 32 moved (all checked on screenshots), 11 hidden, toggle held on all.
- `binder/` (client, `easy_mob_farm`; `BinderSortMessage` both sides, registered `optional()` from `HavenCompatCommon`):
  `BinderFix` (screen events) opens a `BinderSession` per binder screen: search box and `SortDropdown` above the book,
  `CardView` (pure, unit tested: `sorted`, `shown`, `rearrangesAll`). The search moves client slots into view cells
  (`BinderSlotMixin`/`BinderSlotAccessor`, `BinderPagesMixin`); `BinderFix.patched` probes both mixins first. The sort
  is real: `BinderSortMessage(containerId, stateId, order)`; the server refuses a stale state (a click it had not
  answered) and a non-permutation, applies it and `broadcastFullState()`s (one packet, a new state). The client keeps
  one request in flight (`busy()` until the menu state changes, or 40 ticks), holds clicks and slot keys meanwhile (a
  vanilla click with a stale state still runs, on the moved slots), and stops after 3 tries of the same order.
- `signs/SignEditorLayout` (client; `bigsignwriter`, `unlocked_typing`): moves Unlocked Typing's row up one row and
  hides an inactive Done under Big Sign Writer's font list.
- `portals/PortalView` (setting in `config/haven-compat-portal-view.json`, volatile, Fully immersive (the default since 0.43.1; Performance mode in 0.43.0) on
  any read error; levels Fully immersive (`IMMERSIVE`), Performance mode (`PERFORMANCE`), Disabled; 0.43.0 renamed
  Normal and Fast, `SavedChoice.renamed` loads a saved `NORMAL`/`FAST` as the new level; the distance slider is
  `PortalView.DISTANCE`, a `SavedNumber` in `config/haven-compat-portal-distance.json`, default 24 (Shane, 2026-11-25), 8 to 64, shown only
  in Performance mode, saved when let go) + `PortalViewSkipMixin` (Disabled: IP skips every portal view at the HEAD of
  `PortalRenderer.shouldSkipRenderingPortal`; Performance mode at its RETURN: a portal IP would draw while
  `PortalRendering.isRendering()` loses its view and is noted in `PortalView.lostView`, and in the player's own view a
  portal whose `getDistanceToNearestPointInPortal(camera)` is over the distance loses its view) + `PortalViewPlaneMixin`
  -> `PortalPlane` (the portal entity's renderer draws a purple tile per block, vanilla's animated nether portal
  sprite: Disabled; Performance mode in the own view by the distance alone, whoever dropped the view (IP's
  `reducedPortalRendering`, on for a few hours in haven.319, cuts at a hard 16 blocks first: a plane tied to this
  mod's own skip never drew there); inside a view for a portal that lost its view in the last 250 ms; shape getters
  cached per class in a `ClassValue`; a tile is left out when the blocks on both sides of the plane are solid). Test
  (2026-11-25): the rig's `--approach` run steps toward the station portal (48 to 5 blocks): purple at 17, the
  Nether at 15. From a train seat the carriage ahead hides the portal near the switch. The RETURN hook matters: a HEAD hook also catches the back of the portal being
  looked through and its plane covers the whole view (tested). Same effect as IP's `maxPortalLayer: 1` (black
  rectangles there), per player and switchable in play. `HavenSettingsScreen`: Mods > Haven Compat > Config.
- `portals/PortalVisibility` + `PortalVisibilityMixin` / `PortalVisibilityEntryMixin` / `PortalQueryMixin` (client,
  `immersive_portals_core`): IP's `PortalRenderInfo.renderAndDecideVisibility` reads an occlusion query with a blocking
  `glGetQueryObjecti(GL_QUERY_RESULT)`; for a portal hidden in the last frame it reads this frame's own query ("no
  predict": `isFrequentlyMispredicted()` or `queryStallCounter <= 3`), a full CPU-GPU sync per hidden portal per frame.
  The mixins turn "no predict" off, and `GlQueryObject.fetchQueryResult` returns the portal's last decision when
  `GL_QUERY_RESULT_AVAILABLE` is 0 (at most 2 frames in a row, kept per `Visibility` object in a `WeakHashMap`).
  Found with async-profiler in wall mode on the render thread of Shane's real game (28 % at the base, 40 % at a
  portal). Off: `portals.exactVisibility = true`.
- `portals/PipelineMipmaps` (called from `IrisPipelinePrewarmMixin` after every `preparePipeline`, once per pipeline
  object): calls Iris's own `CompositeRenderer.setupMipmapping` for the targets each pass lists in `mipmappedBuffers`.
- `journeymap/JourneyMapReset` + `JourneyMapResetMixin` (client, `journeymap`): `JourneymapClient.loadConfigProperties`
  inside `reset()` runs once per connection.
- Portal performance test method (2026-11-18): a hidden game on a headless sway at the player's real resolution
  (`WLR_RENDER_DRM_DEVICE` = the NVIDIA render node, else it runs on the integrated GPU), a smoke server on a copy of
  the world regions, a throwaway client mod that logs every frame and runs a camera script, async-profiler
  `-e wall -t --filter <render thread tid>`. Dead ends, do not repeat: an empty shadow pass for portal views (the
  Nether has none; in the overworld an empty shadow map lights rooms like open ground), and a scissor rectangle
  around the portal for the portal view's passes (Complementary has passes that need the whole frame: washed-out view).
- Train riders after a portal (0.46.0, `create`; Shane, 2026-11-27: a train's drekki driver missing for players watching the
  train arrive at a station, and for a player riding it from the Nether, until someone sat on that carriage). Three parts,
  each seen on the traintest rig (`run_pair.py --courier --conductor --drekkicond`, the watcher at the coal drill):
  - `mixin/CarriageSnapMixin` (client list; `CarriageSyncData.apply` HEAD, `ContraptionSpawn.snap`): a carriage entity new in
    this game (first 20 ticks) takes its synced place at once. Create's client slides a carriage from where it last had its
    wheels; a carriage that left the overworld at one portal and came back at another stayed at the first portal (454
    blocks off) with its riders. `[contraptions] train carriages new in this game take their synced place at once`.
  - `contraptions/RiderResend` (server): a non-player rider seated on a contraption in that contraption's first 5 seconds
    (riders put back out of a portal or into loaded ground) is taken off the chunk map and added back 2 s later, so every
    game gets it seated; a watcher's game had dropped the driver while its seat was still in the portal. Players never
    (removing one from the chunk map drops it from player tracking). `[contraptions] ... sent again to every player`.
  - `mixin/RiderSpawnPositionMixin` (server, `ServerEntity.sendPairingData`, `ContraptionSpawn.rider`): a contraption
    rider's spawn packet carries its position now (vanilla: the last one sent, never sent for a seated rider; a player rider
    was 1841 blocks off). `[contraptions] ... sent at its seat`.
  - `portals/SeatTurn` + `mixin/SeatTurnMixin` (client, 0.47.1): Create turns a seated player by each frame's step of the
    carriage's view yaw (`ContraptionPlayerPassengerRotation.frame`). After a portal the client's carriage first shows a
    wrong yaw, then swings 90-180 degrees within a tick or two, and Create copied that onto the player (Shane: a quarter
    turn riding the L&S Courier Line from the coal drill into the Nether, 2026-11-27). A step over 45 degrees per tick of
    elapsed time (+5) is not passed on; `[seat turn]` logs it. Test: a copy of the live world (Create's `create_tracks.dat`
    and the regions), riders seated on the real train by the probe's `trainprobe-realride`; the rider on George's carriage
    turned 90-180 degrees at most crossings before. The 0.46.0/0.47.0 `SeatFacing` guard is gone: it measured against the
    same unsettled yaw and turned players itself (`[seat facing] ... turned back`, then a 90 or 180 jump).
  - `contraptions/StorageMenus` + `mixin/MountedStorageMenuMixin` (server, 0.47.0): a storage menu opened on a contraption
    (`MountedItemStorage.handleInteraction`) stays valid only on the carriage entity it was opened on, alive, in the player's
    level; Create checks only alive and 8 blocks, and a train keeps its contraption across portals and unloads with a new entity.
    Rig: a rider's barrel menu closed at the crossing (Create's own check did it there; this rule did not need to); Shane's case
    (coal shown that the chest no longer held) is not reproduced yet. Only these menus: inventory, backpacks and others untouched.
- `contraptions/ContraptionSpawn` + `mixin/ContraptionSpawnPositionMixin` (server, `mixins` list; `create`): `@Pseudo`, the mixin
  extends `Entity` and adds `getAddEntityPacket(ServerEntity)` to Create's `CarriageContraptionEntity` and
  `ControlledContraptionEntity` (siblings; neither they nor `AbstractContraptionEntity` override it): a `ClientboundAddEntityPacket` with the entity's own position, not
  `ServerEntity.getPositionBase()`. Why (Create 6.0.10, NeoForge 21.1 sources): tracking starts from the entity's
  current chunk (`ChunkMap.TrackedEntity.updatePlayer`), the base moves only every update interval (3 for carriages,
  `ServerEntity.sendChanges`), so a carriage that already existed spawned up to 2 ticks of travel behind, in a chunk the
  client was never sent; `TransientEntitySectionManager.addEntity` starts no ticking there, a carriage moves and sets
  `validForRender` only in `tickContraption`, and `isControlledByLocalInstance()` makes the client skip move and teleport
  packets, so it stayed frozen until untracked. Test (2026-11-03): a copy of the live world, Island Express at Village
  Underground and Port Frost Underground, render distance 8 below sim distance 10, a throwaway client probe logs
  each carriage's `tickCount`, chunk and `validForRender`: 0.23.0 3/34 arrivals invisible, 0.24.0 0/35 (17 of them on
  a 0.23.0 client); a player riding the train, render distance 10: 3/11 vs 0/18. Every failure: spawned in an unloaded
  chunk, 0 client ticks. No client fallback: with the real spawn position no carriage spawned in an unloaded chunk.
  A probe must follow each carriage by reference: Immersive Portals sends entities of other dimensions too (other
  client levels), so `minecraft.level.getEntity(id)` misses them.
  Elevators (0.30.8): `ControlledContraptionEntity` (`create:stationary_contraption`, update interval 40) writes
  `ControllerRelative` = controller - `blockPosition()` (server, now) into its spawn data; the client's `readAdditional`
  adds it to the block of the packet position. With a stale base the sum misses the pulley, `getController()` is null,
  `tickContraption` discards the entity on that client only, and it stays gone until the player leaves its range or
  rejoins (a friend's client lost an elevator another player kept seeing and riding, 2026-11-11). Test (2026-11-12,
  hidden, a session in minecraft-rancher-companion): 20 elevator trips each starting out of range, online and single
  player: 0.30.6 0 of 20 visible (the client looked for the pulley 2 to 20 blocks off), the fix 20 of 20.
- `mixin/BackpackNameMixin` (both sides, `mixins` list; `sophisticatedbackpacks`): `@Pseudo`, makes Sophisticated
  Backpacks' `BackpackBlockEntity` `Nameable` from the stored backpack item (`getBackpackWrapper().getBackpack()`, by
  method handle: no compile dependency). Jade names a block from a `Nameable` block entity (server data) or its pick
  item; the backpack had neither, so a placed renamed backpack read "Diamond Backpack". `getDisplayName` (Jade's
  title) stays the plain item name; SecurityCraft's Jade line "Custom name: ..." (`getCustomName`) shows the styled name.
- `mixin/TrashSlotOversizeMixin` + `trash/OversizeDelete` (both sides; `trashslot`): `@Pseudo`. TrashSlot's delete key
  (server, `MessageDeleteFromSlot.attemptDeleteFromSlot`) clicks the slot up and trashes the cursor only when it
  `ItemStack.matches` the whole slot; a slot over one stack (Sophisticated stack upgrades) gives one stack, the match
  fails and the stack stays on the cursor. A slot over its item's max stack size is now clicked up and trashed stack by
  stack until empty, in the one packet (63 clicks for 4000 items, one tick); the trash slot keeps the last stack (undo).
  Test (2026-11-11): Haven Dye Test, iron backpack with a tier 2 stack upgrade, 256 dirt in one slot: 0.30.6 left 64
  on the cursor and 192 in the slot; 0.30.7 (tier 4 upgrade, 1024 dirt) empties the slot in one press, cursor empty.
- `spells/` + `mixin/ArsTouchAimMixin` (both sides, `mixins`) + `mixin/ArsTouchHintMixin` (client) (`ars_nouveau`):
  Ars Nouveau aims a spell book cast on the server (`AbstractCaster.castSpell` -> `SpellUtil.rayTrace`) against the
  server's positions; a small moving mob (a familiar hopping around its owner) was missed on most clicks on a real
  server ("Name only works if I spam it"). The client sends the entity under its crosshair (`TouchTargetMessage`,
  `MultiPlayerGameMode.useItem` HEAD, only for `ars_nouveau` items and only when the server has the channel) before
  the use packet; `@WrapOperation` on the ray returns that entity when it is alive, in the same level and within
  reach + 1.5 blocks (`TouchTargets`, hint used once within 2 ticks), else Ars' own ray. Test (2026-11-12, hidden):
  the server moves the familiar 1 block sideways before the click arrives (lag): 0.30.6 0 of 6, 0.30.7 6 of 6
  in each of 4 runs (Starbuncle and Whirlisprig).
- `mixin/ArsCastOverMobMixin` + `spells/CasterItems` (client; `ars_nouveau`): a right-click on a mob with a spell
  caster casts. Vanilla `Minecraft.startUseItem` asks the mob first (`MultiPlayerGameMode.interactAt`, `interact`) and
  calls `useItem` (Ars casts there: `SpellBook.use` -> `castOnServer`) only when the mob does not consume the click.
  `FamiliarWixie`, `FamiliarBookwyrm`, `FamiliarDrygmy` and `FamiliarAmethystGolem.mobInteract` return `SUCCESS` on the
  client for every click; villagers and pets consume too: within reach no cast was sent. `@WrapOperation` on both
  calls returns `PASS` when the target is a `Mob`, the player does not sneak and the hand holds a caster. The off
  hand's turn is skipped too when the main hand holds a caster: Ars' `use` returns `PASS`, vanilla goes on to the off
  hand, and a villager opens its trades for either hand. A caster (`CasterItems`, by name, cached per class) = the item
  class implements `ICasterTool` and has a `use` of its own above vanilla: spell book, wand, caster tome, gauntlet,
  mirror, scry caster, spell bow, enchanter's fishing rod, Ars Elemental's spell horn. Not the Enchanter's Sword, Spell
  Parchment or Spell Crossbow (`ICasterTool`, vanilla `use`: no cast on a click), not the Dominion Wand. Not skipped:
  entities that are not mobs (boats, minecarts, item frames, armour stands), sneaking, a caster in the off hand while
  the main hand's click is consumed. With a caster in hand a horse is not mounted by a plain click, and Easy
  Villagers takes sneak + click on a villager (pick-up): trade with another item in hand. Test (2026-11-13, a
  throwaway harness mod in a hidden client and a `just smoke --serve` server; the use key through `KeyMapping.click`,
  mobs 2 blocks away, Touch + Name in a novice book; cast = the mob renamed on the server): Wixie, Bookwyrm familiar
  and villager: 0.30.11 no cast (the villager traded), 0.30.12 renamed, no trade. Unchanged in both: Starbuncle
  familiar renamed, gold nugget on it (scrying), empty hand on the villager (trade) and the Wixie, sneak + book
  (the mob's interaction, no cast).
- `contraptions/ContraptionGround` + `mixin/ContraptionMotionMixin` + `mixin/MovePlayerGroundMixin` +
  `mixin/ServerEntityGroundMixin` (both sides, `mixins`; `create`): other players saw a rider of a moving Create
  elevator in the falling or jumping pose (Fresh Animations' player pack reads the on-ground flag, and the flag others
  get is the server's, which is false on most ticks of a moving floor). A rider is sent as on the ground: the flag is
  set at `ServerEntity.sendChanges` HEAD and restored at RETURN, so the server's own state never changes. Rider = in the
  last 40 ticks Create's `ClientMotionPacket` reported contact (going down) or a movement update's y change matched a
  contraption's speed within 0.15 (going up Create's client carries the rider and sends no report), and a contraption
  is within 8 blocks (with lag the server has the rider 2 to 5 blocks behind a 256 RPM floor). On a floor that moves up
  or down the rider always stands; on one at rest a rise above 0.35 per update stays a jump.
  Do not set the server's on-ground flag (0.30.7 to 0.31.0 did, after every movement update): vanilla's
  `handleMovePlayer` then counts each "in the air" update that rises as a jump and charges its hunger, about 5
  saturation points per 150 blocks up at 256 RPM, and the 10 tick contact window ended during laggy descents (falling
  pose in bursts). Create alone charges 3 to 6 exhaustion on that ride (measured with the fix off); we leave that.
  Test (2026-11-14, hidden game through a TCP proxy: 120 ms each way plus a 250 ms stall each second; rope pulley,
  256 RPM, 150 blocks): 0 "in the air" ticks sent while moving, down and up, standing and jumping; hunger up 3.9 with
  the fix, 4.7 and 5.8 without; rider-to-platform gap the same with and without (12 + 4 rides). A rope pulley's stop
  (the contraption becomes blocks) can drop a laggy rider with or without the fix: start test rides from rest.
- `mixin/BackpackRmbDragMixin` + `backpacks/BackpackRmbDrag` (client; `mousetweaks` and `sophisticatedbackpacks`):
  `@Pseudo`, HEAD of Mouse Tweaks' `Main.rmbTweakMaybeClickSlot`. A backpack on the cursor stashes a slot's stack on a
  right-click (`BackpackItem.overrideStackedOnOther`, like a bundle); Mouse Tweaks' right-button drag clicks every slot
  only for a `BundleItem`, else only empty or matching slots. With a backpack on the cursor the drag right-clicks each
  slot holding items it passes (never an empty one: that puts the backpack down); not in the creative inventory.
  Test (2026-11-12, hidden): chest with stone, dirt, sand, gravel, cobblestone; backpack on the cursor, right-button
  drag over the first four: all four stashed, cobblestone left, backpack still on the cursor.
- `mixin/SettingsStacksSyncMixin` (client; `sophisticatedcore`): `@Pseudo`, HEAD of Sophisticated Core's
  `SyncContainerStacksPayload.handlePayload` (HEAD, not TAIL: the method has two returns and TAIL is only the applied
  one). A storage with a stack upgrade (`getStackSizeMultiplier() > 1`) syncs through `HighStackCountSynchronizer`, in
  the storage menu and in `SettingsContainerMenu` alike; the client handler of the full-contents packet applies it only
  to a `StorageContainerMenuBase`, which the settings menu is not (`SyncSlotStackPayload` does accept it), so the
  settings screen drew only the client's own copy of the contents. That copy is empty when a client level load
  (`BackpackStorage.onClientWorldLoad` clears every client copy; Immersive Portals creates a client level per remote
  dimension) comes while the bag screen is open: Settings then showed an empty bag until Back and Settings again. The
  packet is now applied to an open settings menu with the same id (`initializeContents`, what vanilla's contents packet
  does for a storage without a stack upgrade). Serves Sophisticated Storage's settings screens too (same handler; not
  tested there). Test (2026-11-14, hidden client, `just smoke --serve` server, a throwaway harness mod: iron backpack,
  stack upgrade tier 1, 100 dirt; bag open, a `LevelEvent.Load` posted for the client level, Settings): 0.30.12 the
  settings menu's client slots empty, the server's 100 dirt, right on the second open; 0.30.13 right on the first.
  Without the level load both versions were right (contents changed while the bag was closed, by the player or by the
  server while open); a bag without a stack upgrade was right in both (vanilla packet).
- `mixin/MountedBackpackColourMixin` + `backpacks/MountedBackpackColour` (client;
  `sophisticatedbackpackscreateintegration`): `@Pseudo`. A backpack on a moving Create contraption drew white: the
  integration's `clearNbt` empties the backpack's block data in the contraption, and its client refresh
  (`refreshRenderBlockEntity`) calls `BackpackBlockEntity.setBackpack` in Create's virtual render level (not a client
  level), which resolves a server-side wrapper and gets the no-op one (colour -1). The render block entity's colours
  come from `pendingLoadedBackpack` when set, so client ticks set it to the mounted stack until that works (the entity
  link is missing on the first tick), then again after each refresh. Test (2026-11-11): Haven Dye Test, minecart
  contraption with a dyed iron backpack; 0.30.6 white, render BE `Noop` wrapper.
- `data/haven_compat/recipe/createcasing/` (`createcasing` loaded): Create Encased adds 9 vertical gearbox items with no
  recipe; one gearbox alone in a crafting grid converts to its vertical one and back, as Create's own gearbox does.
- `dyeing/` (both sides; registered from `HavenCompatCommon` only with `create`, `create_connected` and
  `create_dragons_plus` loaded; compiles against Create and Ponder/catnip, `compileOnly`): the Brass Dye Nozzle
  (`DyeNozzleItem`, recipe sheet-pipe-sheet over a sheet) turns a Create Connected fan dyeing catalyst into
  `ControlledCatalystBlock` (one per vanilla dye colour, blockstate `position` = `DyePosition` left/right/top/bottom/surround;
  empty-hand right-click cycles, sneak + empty hand takes the nozzle back off). `PositionedColoringType` (one Create fan
  processing type per colour and position, priority 510): Dragons Plus's own coloring recipes first (by reflection,
  `ColoringRecipeInput`), then the crafting grid with the dye in the chosen place (`DyePosition.grid`; Dragons Plus
  always puts the dye right of the item, so Sophisticated items only got their accent), then Dragons Plus's type of the
  colour; particles, air colour and entity dyeing are Dragons Plus's. `NozzleValueBox` (client) draws Create's own
  `ValueBox.TextValueBox` with the position letter through `Outliner` and `CenteredSideValueBoxTransform`, the way
  `ScrollValueRenderer.addBox` does, and Create's hover tip. Assets and data are generated: `python3 tools/dye_nozzle.py`
  (models are Create Connected's `fan_catalyst/with_content` with the core panels cut around an 8 px nozzle per face;
  a face sits at the element's `from` (north, west, down) or `to` (south, east, up) unsorted, and the panels are written
  2 -> 0, so the cut strips stay at 2 / 14 inside the frame. Tags are merged, not overwritten).
- `bleach/` (both sides; registered from `HavenCompatCommon` with `create` loaded; client look `BleachClient`): the Bleach
  fluid (`Bleach`: fluid type, source/flowing, `BleachBlock`, bucket with its own texture from `tools/bleach_bucket.py`: NeoForge's `fluid_container` model
  applies the fluid tint's alpha, so the almost clear fluid made the bucket look empty; no new sources), made by Create mixing (heated: water + salt + bone meal; or liquid chlorine + water + salt with Mekanism).
  Look chosen by the maintainer ("Light"): water's textures tinted #ECF1C4 at about 40 % (tint 0x91ECF1C4), pale fog.
  `BleachCatalyst` (with `create_connected`): the Fan Bleaching Catalyst, made every way Create Connected makes its
  dye and splashing catalysts (Bleach Bucket item application on the empty catalyst, Spout filling 1000 mB, crafted
  back to the empty one), its model their splashing catalyst's composite with tint index 0 coloured by `BleachClient`.
  `BleachExposure`: in the fluid (`entityInside`) or in a fan's stream through it (`BleachingType.affectEntity`):
  Nausea as the sulfur lakes give it (80 ticks, refreshed every 10, ambient) and 1 damage (`haven_compat:bleach`) every
  40 ticks; sheep in the stream turn white. `BleachingType` (Create fan type, the fluid is the catalyst): `BleachProcess`,
  first change wins: Create's own washing recipe when its result has no colour; the water cauldron's dye action
  (vanilla `DYED_ITEM`, `BANNER`, `SHULKER_BOX`; Sophisticated classes and Drekki Rancher's `LeatherWash` by name) run by a `FakePlayer` against a cauldron
  below the build height (refused level change), repeated until it stops (all banner layers); a mod's un-dye recipe
  (`UNDYE_RECIPES`); the family's base item (`DyeFamilies`, read from the crafting recipes: the id without its colour,
  which is an ingredient of the dye recipe); dye data on a `minecraft:dyeable` item; then an item still coloured with no
  base becomes its family's white. Water washing is untouched (the maintainer's call, 2026-11-10). Audit 2026-11-10
  (throwaway proof step over every dyed item): see the pack's patch notes for families no fluid un-dyes.
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
a test copy of "Haven Dev" (made for the test, deleted after; a Haven client with the snapshot world "Haven Snapshot 2026-10-16", the player standing at
-992 63 554 facing the lit portal at -987 64 554, Iris on with Complementary Unbound, `pauseOnLostFocus:false`),
this jar in `mods/`, `OverrideJavaArgs=true` + the flag in `instance.cfg`, then
`prismlauncher -l "<test instance>" -w "Haven Snapshot 2026-10-16"` and a screenshot (`grim`). Pass = the log has
`[portal depth]` "copied by blit" or the stencil hold line, "switched to renderer class ...IrisPortalRenderer" and no
"Switched to compatibility" / "Depth blit ... failed", and the screenshot shows the other side through the portal.
Close the game by working folder (`/proc/<pid>/cwd`), never by name; close Prism before editing `instance.cfg`.
A real Mac check (shaders on, walk through a portal, portal-in-portal) is still owed after each Immersive Portals update.

Server list: give the test instance the bare `minecraft.thedragon.dev` entry in its `servers.dat`
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

## Player page

Players read about this mod on the Haven Codex, tab `#tab-compat` (https://claude.ai/artifact/QG2xPPxSAM2BQiovWVoGiF#tab-compat).
Update that tab when a change alters what players see; publish to that URL in place (never a copy) when the
maintainer asks. The pack repo's CLAUDE.md, "Haven's own mods and pages", lists what to check on a pack change.

## Key hints (haven-create-ui-ux): check them on every feature change

The hotbar mod (haven-create-ui-ux 1.6.0+, repo minecraft-create-ui-remix) shows **contextual key hints** (centered
above the action bar: what the crosshair's target does with the held item) and **stateful key hints** (bottom right:
what the player rides, holds and wears, with modes and fuel; the Escape menu's global keys). Each hint comes from the
owning mod's own checks, called by class and method name (KeyRules, KeyCreate, KeyMods, KeyVanilla, KeyFluids), or from
audited data (`mod/keyhints`, remade with its `scan.py` / `scan2.py` and `merge.py`).

When this repo adds, removes or changes a right-click use, a held item's use, a key mapping, a mode, a worn device or
a readout (fuel, charge, level), update the hints in minecraft-create-ui-remix in the same piece of work: a rule, or a
data row, and a test scene. A rename of a class or method that a hint names by string fails silently (the hint just
disappears), so search minecraft-create-ui-remix for the old name before a rename.

This repo: its key changes (ModeHand, NTGL key gates, map keys) and pack-default key moves show in the hints; keep both in step.
