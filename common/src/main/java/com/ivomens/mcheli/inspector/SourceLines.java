package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.content.bind.YamlLineLocator;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.content.pack.AddonPack;
import com.ivomens.mcheli.content.pack.BuiltinAddonPack;
import com.ivomens.mcheli.content.registry.ContentEntry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The text of a content file read again from its pack, to find the line a setting is on. The schema keeps no
 * positions, so this looks the key up the way parse errors do: by its YAML path, or by its TXT key. A file
 * that cannot be read (a pack removed since it loaded) gives no lines.
 */
final class SourceLines {

    static final SourceLines NONE = new SourceLines("", List.of(), false);

    private final String file;
    private final List<String> lines;
    private final boolean txt;

    private SourceLines(String file, List<String> lines, boolean txt) {
        this.file = file;
        this.lines = lines;
        this.txt = txt;
    }

    static SourceLines of(ContentEntry<?> entry) {
        String domain = entry.location().getAddonDomain();
        AddonPack pack = AddonManager.BUILTIN_ADDON_DOMAIN.equals(domain) ? BuiltinAddonPack.instance()
                : AddonManager.get(domain);
        String name = entry.filePath().substring(entry.filePath().lastIndexOf('/') + 1);
        if (pack == null || pack.getFile() == null) {
            return new SourceLines(name, List.of(), entry.isLegacyTxt());
        }
        return new SourceLines(name, read(pack.getFile(), entry.filePath()), entry.isLegacyTxt());
    }

    private static List<String> read(File root, String filePath) {
        try {
            if (root.isDirectory()) {
                Path base = root.toPath().toAbsolutePath().normalize();
                Path path = base.resolve(filePath).normalize();
                return path.startsWith(base) && Files.isRegularFile(path)
                        ? Files.readAllLines(path, StandardCharsets.UTF_8) : List.of();
            }
            try (ZipFile zip = new ZipFile(root)) {
                ZipEntry zipped = zip.getEntry(filePath);
                if (zipped == null) {
                    return List.of();
                }
                try (InputStream in = zip.getInputStream(zipped)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
                }
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** The file's name, or empty when there is no file. */
    String file() {
        return this.file;
    }

    /**
     * The 1-based line that sets a value, or 0 when the file does not set it (or cannot be read).
     *
     * @param yamlPath the key's path in a YAML file, such as {@code Physics.Speed}
     */
    int lineOf(String yamlPath, List<String> aliases, List<String> txtKeys) {
        if (this.lines.isEmpty()) {
            return 0;
        }
        if (this.txt) {
            return txtLine(txtKeys);
        }
        int dot = yamlPath.lastIndexOf('.');
        String parent = dot < 0 ? "" : yamlPath.substring(0, dot + 1);
        int line = yamlLine(yamlPath, yamlPath.substring(dot + 1));
        for (int i = 0; line == 0 && i < aliases.size(); i++) {
            line = yamlLine(parent + aliases.get(i), aliases.get(i));
        }
        return line;
    }

    /** The locator gives the deepest part it found; only a line naming the key itself counts. */
    private int yamlLine(String path, String key) {
        int line = YamlLineLocator.find(this.lines, path);
        if (line <= 0 || line > this.lines.size()) {
            return 0;
        }
        String text = this.lines.get(line - 1).trim();
        if (text.startsWith("- ")) {
            text = text.substring(2).trim();
        }
        return text.startsWith(key + ":") || text.startsWith("\"" + key + "\":") ? line : 0;
    }

    private int txtLine(List<String> txtKeys) {
        for (int i = 0; i < this.lines.size(); i++) {
            String text = this.lines.get(i).trim();
            int separator = text.indexOf('=');
            if (separator > 0 && txtKeys.contains(text.substring(0, separator).trim().toLowerCase(Locale.ROOT))) {
                return i + 1;
            }
        }
        return 0;
    }
}
