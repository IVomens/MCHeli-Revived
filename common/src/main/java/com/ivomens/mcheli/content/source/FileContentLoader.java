package com.ivomens.mcheli.content.source;

import com.ivomens.mcheli.content.pack.ZipFileCleanupTracker;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.util.ModLogger;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Loads content from an addon packed as a zip or jar file, in path order. */
public class FileContentLoader extends ContentLoader implements Closeable {

    /** Opened on first use; the cleanup tracker closes it if this loader is never closed explicitly. */
    private final AtomicReference<ZipFile> zipFile = new AtomicReference<>();

    public FileContentLoader(String domain, File addonDir, String loaderVersion, Predicate<String> fileFilter) {
        super(domain, addonDir, loaderVersion, fileFilter);
        ZipFileCleanupTracker.track(this, this.zipFile, addonDir);
    }

    private ZipFile getZipFile() throws IOException {
        return ZipFileCleanupTracker.getOrOpen(this.zipFile, this.dir);
    }

    @Override
    protected List<PackFile> getEntries() {
        boolean wholePack = this.searchesWholePack();
        List<PackFile> list = new ArrayList<>();
        try {
            Iterator<? extends ZipEntry> entries =
                    this.getZipFile().stream().filter(entry -> this.isCandidate(entry, wholePack))
                            .sorted(Comparator.comparing(ZipEntry::getName)).iterator();
            while (entries.hasNext()) {
                String name = entries.next().getName();
                // assets/mcheli/<type folder>/...
                String[] parts = name.split("/");
                ContentType type = ContentType.fromDirName(parts.length >= 3 ? parts[2] : null);
                if (type != null) {
                    list.add(this.makeEntry(name, type, false));
                }
            }
        } catch (IOException e) {
            ModLogger.get().error("Could not read addon archive '{}'", this.dir, e);
        }
        return list;
    }

    private boolean isCandidate(ZipEntry entry, boolean wholePack) {
        if (entry.isDirectory()) {
            return false;
        }
        String name = entry.getName();
        String[] parts = name.split("/");
        String rootDir = parts.length >= 2 ? parts[0] : "";
        return (wholePack || "assets".equals(rootDir) || parts.length <= 2) && this.isReadable(name);
    }

    @Override
    protected InputStream getInputStreamByName(String name) throws IOException {
        ZipFile zip = this.getZipFile();
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new FileNotFoundException(String.format("'%s' in AddonPack '%s'", name, this.dir));
        }
        return zip.getInputStream(entry);
    }

    @Override
    public void close() throws IOException {
        ZipFileCleanupTracker.close(this.zipFile);
    }
}
