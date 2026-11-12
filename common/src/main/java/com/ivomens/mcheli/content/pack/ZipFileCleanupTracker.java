package com.ivomens.mcheli.content.pack;

import com.ivomens.mcheli.util.ModLogger;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

/**
 * Lazily opened zip files shared by the addon loaders. A loader that is dropped without being closed still
 * gets its zip file closed once it is garbage collected.
 */
public final class ZipFileCleanupTracker {

    private static final Cleaner CLEANER = Cleaner.create();

    private ZipFileCleanupTracker() {}

    /** Closes the zip held in {@code zipFileRef} when {@code owner} becomes unreachable. */
    public static void track(Object owner, AtomicReference<ZipFile> zipFileRef, File source) {
        CLEANER.register(owner, () -> {
            try {
                close(zipFileRef);
            } catch (IOException e) {
                ModLogger.error("Failed to close leaked zip file: " + source, e);
            }
        });
    }

    /** Returns the zip held in {@code zipFileRef}, opening {@code file} on first use. */
    public static ZipFile getOrOpen(AtomicReference<ZipFile> zipFileRef, File file) throws IOException {
        ZipFile zipFile = zipFileRef.get();
        if (zipFile != null) {
            return zipFile;
        }
        synchronized (zipFileRef) {
            zipFile = zipFileRef.get();
            if (zipFile == null) {
                zipFile = new ZipFile(file);
                zipFileRef.set(zipFile);
            }
            return zipFile;
        }
    }

    public static void close(AtomicReference<ZipFile> zipFileRef) throws IOException {
        ZipFile zipFile = zipFileRef.getAndSet(null);
        if (zipFile != null) {
            zipFile.close();
        }
    }
}
