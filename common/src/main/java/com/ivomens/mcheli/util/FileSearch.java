package com.ivomens.mcheli.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeSet;

/** Collects files below a directory whose names match a pattern. Results accumulate across calls. */
public class FileSearch {

    public static final int TYPE_FILE_OR_DIR = 1;
    public static final int TYPE_FILE = 2;
    public static final int TYPE_DIR = 3;
    private static final long ONE_DAY_MS = 86_400_000L;
    private final TreeSet<File> set = new TreeSet<>();

    /** Lists files recursively; {@code fileName} may use {@code *} as a wildcard. */
    public File[] listFiles(String directoryPath, String fileName) {
        if (fileName != null) {
            fileName = fileName.replace(".", "\\.");
            fileName = fileName.replace("*", ".*");
        }
        return this.listFiles(directoryPath, fileName, TYPE_FILE, true, 0);
    }

    /**
     * @param fileNamePattern regular expression for the file name, or null for any name
     * @param type            one of the {@code TYPE_*} constants
     * @param period          0 for any age; a negative value skips files modified within the last
     *                        {@code -period} days
     */
    public File[] listFiles(String directoryPath, String fileNamePattern, int type, boolean isRecursive, int period) {
        File dir = new File(directoryPath);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + dir.getAbsolutePath());
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                this.addFile(type, fileNamePattern, file, period);
                if (isRecursive && file.isDirectory()) {
                    this.listFiles(file.getAbsolutePath(), fileNamePattern, type, true, period);
                }
            }
        }
        return this.set.toArray(new File[0]);
    }

    private void addFile(int type, String match, File file, int period) {
        if (type == TYPE_FILE && !file.isFile() || type == TYPE_DIR && !file.isDirectory()) {
            return;
        }
        if (match != null && !file.getName().matches(match)) {
            return;
        }
        if (period < 0) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd");
            String lastModifiedDay = dayFormat.format(new Date(file.lastModified()));
            String limitDay = dayFormat.format(new Date(System.currentTimeMillis() - ONE_DAY_MS * -period));
            if (lastModifiedDay.compareTo(limitDay) > 0) {
                return;
            }
        }
        this.set.add(file);
    }

    public void clear() {
        this.set.clear();
    }
}
