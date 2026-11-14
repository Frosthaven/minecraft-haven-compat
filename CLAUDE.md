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
- `trees/HangingLogs` (pure, unit tested) + `mixin/ArsTreeCutMixin` (server, `mixins` list; `create`, `ars_nouveau`): a
  Mechanical Saw takes the whole Ars tree. Why (Create 6.0.10, Ars Nouveau 5.13.3): `TreeCutter.findTree` follows logs
  sideways and up only; Ars' `MagicTrunkPlacer.addBranch` ends each branch with a log one block under the branch tip,
  which stayed in the air with its leaves (8 single logs above a tree farm, 2026-11-13). The mixin adds, before the leaf
  search, each log of an `ars_*` mod right under a found log, above the cut level, with no log under it. Test
  (2026-11-13): `just smoke --serve` with the jar, `place feature ars_nouveau:vexing_tree` and `cascading_tree`, three
  base logs removed, a powered saw on the fourth: no log left above the base level.
- `schematics/PrintOwner` + `mixin/CannonPrintStarterMixin` + `mixin/CannonDeployerOwnerMixin` (server, `mixins` list;
  `create`): a Deployer printed by a Schematicannon takes the player who started the print as its owner. Why (Create
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
- `defaults/LoadDefaults` writes options.txt as text (`OptionsFile`), never `Options.save()`: at
  `Options.loadSelectedResourcePacks` only vanilla key mappings exist, and a save there dropped every mod key binding.
- `PackDefaults`: the pack's `config/haven-pack-defaults.json` (`file()`, `section(file, key)`, `root(file)`); each fix
  reads its own key. `Os.macOS()`: the OS check (`os.name`, or `-Dhaven_compat.assumeMac=true`).
- `defaults/`: the pack's one-time defaults (from haven-create-ui-ux up to 1.3.0). `DefaultsState`: `config/
  haven-pack-defaults.state.json` (keys `seen`, `shaderApplied`, `shaderOptions`, `ordered`, `darkLoadingScreen`, `keys`). `LoadDefaults`
  (`mixin/LoadDefaultsMixin`, TAIL of `Options.loadSelectedResourcePacks`, once; before the first resource load, so no
  reload): `resourcePacks` enabled once in order (`PackList` pure, unit tested), `resourcePackOrder` rules once each (state
  `ordered`, `PackOrder` pure, unit tested), `darkLoadingScreen` sets `darkMojangStudiosBackground` once, `keys` rules move a key line once from its old default
  (`KeyDefaults` pure, unit tested; a key the player bound stays; mod keys read options.txt later in the same start); the pack lists
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
- `portals/PortalView` (setting in `config/haven-compat-portal-view.json`, volatile, Normal on any read
  error) + `PortalViewSkipMixin` (Disabled: IP skips the portal view) + `PortalViewPlaneMixin` -> `PortalPlane` (the
  portal entity's renderer draws a purple tile per block; shape getters cached per class in a `ClassValue`; a tile is
  left out when the blocks on both sides of the plane are solid). `HavenSettingsScreen`: Mods > Haven Compat > Config.
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
- `contraptions/ContraptionGround` + `mixin/ContraptionMotionMixin` + `mixin/MovePlayerGroundMixin` (both sides,
  `mixins`; `create`): other players saw a rider of a descending Create elevator in the falling pose (Fresh Animations'
  player pack: not on ground and going down). The on-ground flag others get is the server's; both the rider's movement
  packet and Create's `ClientMotionPacket` (sent while touching a contraption, with that moment's on-ground flag) say
  "in the air" on some ticks while the floor drops. A player Create reported touching a contraption in the last 10
  ticks, not rising more than 0.35 since the last movement update (a jump rises ~0.42), not flying, gliding or riding, stays on the ground
  (after `ClientMotionPacket.handle` and `handleMovePlayer`, and at `ServerEntity.sendChanges` HEAD: something in the
  tick clears it again, so the broadcast moment is the one that counts). Test (2026-11-12, hidden): rope pulley at 128 RPM down,
  server on-ground (what others get) over 40 ticks: 0.30.6 false on 3 to 4 mid-descent ticks, 0.30.7 none once
  moving (the first ticks, before Create reports contact, stay a real hop).
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
  (vanilla `DYED_ITEM`, `BANNER`, `SHULKER_BOX`; Sophisticated classes by name) run by a `FakePlayer` against a cauldron
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
