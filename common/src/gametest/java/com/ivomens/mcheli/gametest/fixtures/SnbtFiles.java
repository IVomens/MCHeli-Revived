package com.ivomens.mcheli.gametest.fixtures;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Reading, writing and comparing the SNBT save fixtures. */
final class SnbtFiles {

    private SnbtFiles() {
    }

    static CompoundTag read(Path file) throws IOException {
        try {
            return TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8));
        } catch (CommandSyntaxException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** Writes the tag as sorted, indented SNBT, unless the file already holds an equal tag. */
    static void writeIfChanged(Path file, CompoundTag tag) throws IOException {
        if (Files.isRegularFile(file) && read(file).equals(tag)) {
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, new SnbtPrinterTagVisitor().visit(tag) + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Checks that {@code resaved} still has every key of {@code original} with an equal value; it may
     * have more. Returns the differing keys, or null.
     */
    static String missingOrChanged(CompoundTag original, CompoundTag resaved, String... ignored) {
        Set<String> skip = Set.of(ignored);
        List<String> problems = new ArrayList<>();
        for (String key : new TreeSet<>(original.getAllKeys())) {
            if (skip.contains(key)) {
                continue;
            }
            Tag before = original.get(key);
            Tag after = resaved.get(key);
            if (after == null) {
                problems.add(key + " missing");
            } else if (!before.equals(after)) {
                problems.add(key + " changed from " + before + " to " + after);
            }
        }
        return problems.isEmpty() ? null : "saved again with " + String.join(", ", problems);
    }
}
