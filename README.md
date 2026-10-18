# Haven Compat

Client-side fixes between mods of the Haven server's modpack (Minecraft 1.21.1, NeoForge) that belong to none of the
mods themselves. Each fix switches itself off when the mods it is about are not installed, so the jar is safe in any
pack. Nothing to configure.

| Fix | Mods | What it does |
|---|---|---|
| Portals with shaders on macOS | Immersive Portals (Aeronautics fork), Iris | Portals render through with shaders on, on a computer whose OpenGL has no `glCopyImageSubData` (OpenGL 4.3; macOS stops at 4.1). Without it the game aborts near a portal the moment shaders go on. |

## Download

**[Latest build](https://github.com/Frosthaven/minecraft-haven-compat/releases/latest)**: drop `haven-compat.jar` into
`mods/` in the client instance. The Haven pack delivers it by itself (`mods/haven-compat.pw.toml`); nothing goes on the
server.

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

## Development

`./gradlew build` (Gradle on JDK 17 or 21, mod on Java 21). Other mods are reached by class name only (mixin targets,
reflection), never on the compile classpath. Every push to `main` publishes a rolling `latest` release and an immutable
`v<version>-<commit>` release, which the Haven pack pins by hash (`just bump-mod haven-compat` in minecraft-haven-pack).
