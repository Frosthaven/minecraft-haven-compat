package dev.frosthaven.havencompat.colorwheel;

import dev.frosthaven.havencompat.Log;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL41;

/**
 * Colorwheel's linked programs, kept on disk. The driver's own shader cache makes compiling a shader quick, but each
 * game start still links every program again from it: about 5 ms each, 51 programs for each of 17 engine and pipeline
 * pairs, 72% of the time the Colorwheel part of the shader prewarm takes (2026-11-22). A linked program can be read back
 * from the driver ({@code glGetProgramBinary}) and loaded in place of a link ({@code glProgramBinary}).
 * <p>
 * {@link #link} stands in for {@code glLinkProgram} in Colorwheel's linker. The key is a hash of the sources of the
 * attached shaders and of the driver (vendor, renderer, version): any change of the shader pack, its settings, a mod's
 * shader code or the driver gives new keys, so a program is linked only when something was updated. A binary the driver
 * refuses is deleted and the program is linked as before. Files: {@value #DIR} in the game folder.
 */
public final class ProgramBinaries {
    public static final String DIR = "haven/program-binaries";
    private static final Log LOG = new Log("[program cache] ");
    private static String driver;
    private static Boolean usable;
    private static int loaded, linked;
    /** programs of this session and their files: saved again once they have been drawn with (see {@link #saveUsed}) */
    private static final java.util.Map<Integer, Path> SESSION = new java.util.LinkedHashMap<>();
    private static long nextSave;

    private ProgramBinaries() {}

    public static void link(int program) {
        if (!usable()) { GL20.glLinkProgram(program); return; }
        Path file = null;
        try {
            file = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR).resolve(key(program) + ".bin");
            if (Files.exists(file)) {
                byte[] bytes = Files.readAllBytes(file);
                ByteBuffer data = BufferUtils.createByteBuffer(bytes.length - 4);
                data.put(bytes, 4, bytes.length - 4).flip();
                int format = ByteBuffer.wrap(bytes, 0, 4).getInt();
                GL41.glProgramBinary(program, format, data);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE) {
                    if (++loaded == 1) LOG.info("Colorwheel's programs are loaded from {} when their shaders and the driver are unchanged", DIR);
                    SESSION.put(program, file);
                    return;
                }
                while (GL11.glGetError() != GL11.GL_NO_ERROR) { }   // the refusal is no error of the game's
                Files.deleteIfExists(file);
            }
        } catch (Exception | LinkageError e) {
            LOG.warnOnce("load", "could not load a program from {} ({}); it is linked as before", DIR, e.toString());
        }
        GL20.glLinkProgram(program);
        linked++;
        if (file == null) return;
        SESSION.put(program, file);
        save(program, file);
    }

    /**
     * The driver finishes a program's machine code at the first draw with it, for the state it is drawn in, not at the
     * link: the first Colorwheel draws after a portal crossing stalled 0.3 s in the driver although every program was
     * linked at the game's start (2026-11-22). A binary read back after the program has been drawn with carries that
     * finished code. So the session's programs are saved again a while into play and then every few minutes: the next
     * game start loads programs that are ready to draw. Called once a client tick; does its work in slices.
     */
    public static void saveUsed() {
        if (SESSION.isEmpty() || Minecraft.getInstance().level == null) { nextSave = 0; return; }
        long now = System.currentTimeMillis();
        if (nextSave == 0) nextSave = now + 90_000;
        if (now < nextSave) return;
        if (pending == null) pending = new java.util.ArrayDeque<>(SESSION.entrySet());
        for (int i = 0; i < 12 && !pending.isEmpty(); i++) {   // a dozen a tick: no long frame from the saving itself
            var e = pending.poll();
            if (GL20.glIsProgram(e.getKey())) save(e.getKey(), e.getValue()); else SESSION.remove(e.getKey());
        }
        if (pending.isEmpty()) {
            pending = null;
            nextSave = now + 300_000;
            LOG.once("resaved", "Colorwheel's programs were saved again after use, with the driver's finished code");
        }
    }

    private static java.util.ArrayDeque<java.util.Map.Entry<Integer, Path>> pending;

    private static void save(int program, Path file) {
        try {
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) return;
            int length = GL20.glGetProgrami(program, GL41.GL_PROGRAM_BINARY_LENGTH);
            if (length <= 0) return;
            ByteBuffer data = BufferUtils.createByteBuffer(length);
            int[] size = new int[1], format = new int[1];
            GL41.glGetProgramBinary(program, size, format, data);
            byte[] out = new byte[size[0] + 4];
            ByteBuffer.wrap(out).putInt(format[0]);
            data.get(out, 4, size[0]);
            Path target = file;
            net.minecraft.Util.ioPool().execute(() -> {
                try {
                    Files.createDirectories(target.getParent());
                    Path part = target.resolveSibling(target.getFileName() + "." + program + ".part");   // two programs can have one key
                    Files.write(part, out);
                    Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    LOG.warnOnce("save", "could not save a program to {} ({})", DIR, e.toString());
                }
            });
        } catch (Exception | LinkageError e) {
            LOG.warnOnce("save", "could not read a program back from the driver ({}); it is not cached", e.toString());
        }
    }

    /** How many programs this session loaded from disk and how many it linked, for the prewarm's log line. */
    public static String counts() {
        return loaded + " loaded from disk, " + linked + " linked";
    }

    private static boolean usable() {
        if (usable == null) {
            var caps = GL.getCapabilities();
            usable = (caps.OpenGL41 || caps.GL_ARB_get_program_binary) && GL11.glGetInteger(GL41.GL_NUM_PROGRAM_BINARY_FORMATS) > 0;
            driver = GL11.glGetString(GL11.GL_VENDOR) + "|" + GL11.glGetString(GL11.GL_RENDERER) + "|" + GL11.glGetString(GL11.GL_VERSION);
            if (!usable) LOG.once("none", "this driver gives no program binaries; Colorwheel's programs are linked at every game start");
        }
        return usable;
    }

    private static String key(int program) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        sha.update(driver.getBytes(StandardCharsets.UTF_8));
        int[] count = new int[1], shaders = new int[16];
        GL20.glGetAttachedShaders(program, count, shaders);
        for (int i = 0; i < count[0]; i++) {
            sha.update((byte) GL20.glGetShaderi(shaders[i], GL20.GL_SHADER_TYPE));
            sha.update(GL20.glGetShaderSource(shaders[i]).getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(sha.digest());
    }
}
