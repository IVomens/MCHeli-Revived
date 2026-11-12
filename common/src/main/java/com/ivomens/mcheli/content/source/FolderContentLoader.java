package com.ivomens.mcheli.content.source;

import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.util.ModLogger;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Loads content from an addon that is an unpacked folder. */
public class FolderContentLoader extends ContentLoader {

    private final Path root;

    public FolderContentLoader(String domain, File addonDir, String loaderVersion, Predicate<String> fileFilter) {
        super(domain, addonDir, loaderVersion, fileFilter);
        Path resolved;
        try {
            resolved = addonDir.toPath().toRealPath();
        } catch (IOException e) {
            resolved = addonDir.toPath().toAbsolutePath().normalize();
        }
        this.root = resolved;
    }

    @Override
    protected List<PackFile> getEntries() {
        List<PackFile> entries = new ArrayList<>();
        walk(this.dir, null, this.searchesWholePack(), 0, entries);
        return entries;
    }

    /**
     * Collects content files below {@code node}, in path order. The content type comes from the first folder on the
     * path that is named like a type folder. Without a whole-pack search only the pack root, its direct children and
     * everything below {@code assets/} are visited.
     */
    private void walk(File node, @Nullable ContentType type, boolean descendFreely, int depth,
                      List<PackFile> out) {
        if (!node.exists()) {
            return;
        }

        if (node.isDirectory()) {
            if (!descendFreely && depth > 1) {
                return;
            }
            File[] children = node.listFiles();
            if (children == null) {
                return;
            }
            // by name, so the file that wins among two of the same name is the same on every system
            Arrays.sort(children, Comparator.comparing(File::getName));
            for (File child : children) {
                ContentType childType = type != null ? type : ContentType.fromDirName(child.getName());
                boolean childDescendsFreely = descendFreely || (depth == 0 && "assets".equals(child.getName()));
                walk(child, childType, childDescendsFreely, depth + 1, out);
            }
            return;
        }

        try {
            String relativePath = relativePath(node);
            if (type != null && this.isReadable(relativePath)) {
                out.add(this.makeEntry(relativePath, type, false));
            }
        } catch (IOException e) {
            ModLogger.get().error("Could not read '{}' in addon '{}'", node, this.domain, e);
        }
    }

    private String relativePath(File file) throws IOException {
        Path realFile;
        try {
            realFile = file.toPath().toRealPath();
        } catch (IOException e) {
            throw new FileNotFoundException(file.getAbsolutePath());
        }

        Path relative = this.root.relativize(realFile).normalize();
        if (relative.startsWith("..")) {
            throw new SecurityException("Resolved file escapes addon root: " + realFile);
        }
        return relative.toString().replace('\\', '/');
    }

    @Override
    protected InputStream getInputStreamByName(String name) throws IOException {
        File file = resolveInsideRoot(name);
        if (file == null) {
            throw new FileNotFoundException(String.format("'%s' in AddonPack '%s'", name, this.dir));
        }
        return new BufferedInputStream(Files.newInputStream(file.toPath()));
    }

    /** Resolves a pack-relative path, refusing anything that points outside the pack folder. */
    @Nullable
    private File resolveInsideRoot(String name) {
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path candidate;
        try {
            candidate = this.root.resolve(normalized).normalize();
        } catch (InvalidPathException e) {
            return null;
        }
        if (!candidate.startsWith(this.root) || !Files.isRegularFile(candidate)) {
            return null;
        }
        return candidate.toFile();
    }
}
