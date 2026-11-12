package com.ivomens.mcheli.multiplay.client;

import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.multiplay.net.ModListMessage;
import com.ivomens.mcheli.multiplay.net.ScreenshotChunkMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.platform.PlatformHelper;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.FileSearch;
import com.ivomens.mcheli.util.ModLogger;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * What this client reports to an operator through the multiplayer manager: a screenshot, uploaded in
 * chunks a few ticks apart, and the list of installed mods.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class OperatorReports {

    private static final int CHUNK_SIZE = ScreenshotChunkMessage.MAX_CHUNK;
    /** A mod list message is sent once its lines add up to more than this many characters. */
    private static final int MOD_LIST_CHUNK_CHARS = 1024;
    private static final int SEND_INTERVAL_TICKS = 10;

    /** PNG of the screenshot being uploaded, or {@code null} when there is none. */
    private byte[] imageData;
    private int nextChunk;
    private List<String> modList = new ArrayList<>();
    private int ticksSinceSend;

    /** Once per client tick: sends the next screenshot chunk when it is due. */
    public void tick() {
        if (++this.ticksSinceSend < SEND_INTERVAL_TICKS) return;
        sendImageData();
        this.ticksSinceSend = 0;
    }

    /** Captures the current frame; the PNG is then uploaded in chunks a few ticks apart. */
    public void startSendImageData() {
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            this.imageData = image.asByteArray();
            this.nextChunk = 0;
        } catch (Exception e) {
            ModLogger.log("Screenshot capture failed: %s", e);
        }
    }

    /** Sends the next chunk of the screenshot being uploaded, if any. */
    private void sendImageData() {
        byte[] data = this.imageData;
        if (data == null) return;

        int start = this.nextChunk * CHUNK_SIZE;
        if (start < data.length) {
            byte[] chunk = Arrays.copyOfRange(data, start, Math.min(start + CHUNK_SIZE, data.length));
            Network.sendToServer(new ScreenshotChunkMessage(start, data.length, chunk));
            this.nextChunk++;
        }
        if (this.nextChunk * CHUNK_SIZE >= data.length) {
            this.imageData = null;
            this.nextChunk = 0;
        }
    }

    /** Collects the class path, active mods and jar manifests for an operator's mod list request. */
    public void readModList(String playerName, String commandSenderName) {
        this.modList = new ArrayList<>();
        this.modList.add(ChatFormatting.RED + "###### Name:" + commandSenderName + " ######");
        this.modList.add(ChatFormatting.RED + "###### ID  :" + playerName + " ######");
        // The game jar sits in the launcher's versions folder; its size reveals a modified client.
        for (String classPathEntry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (classPathEntry.length() <= 1) continue;
            File file = new File(classPathEntry);
            if (file.getAbsolutePath().toLowerCase(Locale.ROOT).contains("versions")) {
                this.modList.add(ChatFormatting.AQUA + "# Client class=" + file.getName() + " : file size= " + file.length());
            }
        }

        this.modList.add(ChatFormatting.YELLOW + "=== ActiveModList ===");
        for (PlatformHelper.LoadedMod mod : Services.PLATFORM.getLoadedMods()) {
            this.modList.add(mod.id() + "  " + mod.displayName() + "[" + mod.version() + "]  " + mod.fileName());
        }

        Minecraft mc = Minecraft.getInstance();
        File modsDir = new File(mc.gameDirectory, "mods");
        File[] files = new FileSearch().listFiles(modsDir.getAbsolutePath(), "*.jar");
        this.modList.add(ChatFormatting.YELLOW + "=== Manifest ===");
        if (files != null) {
            for (File file : files) {
                try {
                    String manifest = manifestOf(file);
                    if (!manifest.isEmpty()) {
                        this.modList.add(file.getName() + manifest);
                    }
                } catch (Exception e) {
                    this.modList.add(file.getName() + " : Read Manifest failed.");
                }
            }
        }
    }

    /** The jar's manifest lines without spaces, each as {@code " [line]"}; empty without a manifest. */
    private static String manifestOf(File file) throws Exception {
        StringBuilder manifest = new StringBuilder();
        try (JarFile jarFile = new JarFile(file.getCanonicalPath())) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                ZipEntry entry = jarEntries.nextElement();
                if (entry.getName().equalsIgnoreCase("META-INF/MANIFEST.MF") && !entry.isDirectory()) {
                    try (InputStream in = jarFile.getInputStream(entry);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                            line = line.replace(" ", "").trim();
                            if (!line.isEmpty()) {
                                manifest.append(" [").append(line).append("]");
                            }
                        }
                    }
                    break;
                }
            }
        }
        return manifest.toString();
    }

    public void sendModsInfo(String playerName, String commandSenderName, int id) {
        if (McheliConfig.debug().debugLog()) {
            this.modList.clear();
            readModList(playerName, commandSenderName);
        }
        sendModList(this.modList, id);
    }

    /** Sends the list in chunks of about {@link #MOD_LIST_CHUNK_CHARS} characters; long lines are cut. */
    private static void sendModList(List<String> lines, int requestId) {
        List<String> chunk = new ArrayList<>();
        int chars = 0;
        boolean first = true;
        for (String line : lines) {
            String cut = line.substring(0, Math.min(line.length(), ModListMessage.MAX_LINE_LENGTH));
            chunk.add(cut);
            chars += cut.length() + 2;
            if (chars > MOD_LIST_CHUNK_CHARS || chunk.size() == ModListMessage.MAX_LINES) {
                Network.sendToServer(new ModListMessage(requestId, first, chunk));
                first = false;
                chunk.clear();
                chars = 0;
            }
        }
        if (!chunk.isEmpty()) {
            Network.sendToServer(new ModListMessage(requestId, first, chunk));
        }
    }
}
