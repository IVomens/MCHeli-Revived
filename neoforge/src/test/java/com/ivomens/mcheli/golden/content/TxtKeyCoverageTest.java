package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.core.content.def.hud.HudCommands;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableSchema;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.content.def.weapon.WeaponSchema;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.core.content.schema.Schema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails when a TXT key the game reads appears in no TXT fixture, so a key added to a reader cannot slip
 * past the content dump. Types read through a content schema list their keys there, HUD commands in the
 * HUD command table.
 */
class TxtKeyCoverageTest {

    @Test
    void everyHudKeyAppearsInAFixture() {
        Set<String> uncovered = new TreeSet<>(HudCommands.txtKeys());
        uncovered.addAll(List.of("if", "endif"));
        uncovered.removeAll(fixtureKeys(List.of(ContentType.HUD)));
        assertTrue(uncovered.isEmpty(), "HUD commands with no TXT fixture line (add them to "
                + TxtFixtures.pack() + "): " + uncovered);
    }

    /** Content types read through a schema, which lists their TXT keys. */
    private static final Map<ContentType, Schema<?>> SCHEMAS = Map.of(
            ContentType.THROWABLE, ThrowableSchema.SCHEMA,
            ContentType.WEAPON, WeaponSchema.SCHEMA,
            ContentType.HELICOPTER, VehicleSchema.forKind(VehicleKind.HELICOPTER),
            ContentType.PLANE, VehicleSchema.forKind(VehicleKind.PLANE),
            ContentType.SHIP, VehicleSchema.forKind(VehicleKind.SHIP),
            ContentType.TANK, VehicleSchema.forKind(VehicleKind.TANK),
            ContentType.VEHICLE, VehicleSchema.forKind(VehicleKind.VEHICLE));

    /** The vehicle kinds share most keys; a shared key only needs a line in one kind's fixtures. */
    private static final List<ContentType> VEHICLES = List.of(ContentType.HELICOPTER, ContentType.PLANE,
            ContentType.SHIP, ContentType.TANK, ContentType.VEHICLE);

    @Test
    void everySchemaKeyAppearsInAFixture() {
        Map<ContentType, Set<String>> missing = new TreeMap<>();
        SCHEMAS.forEach((type, schema) -> {
            Set<String> uncovered = new TreeSet<>(schema.txtKeys());
            uncovered.removeAll(fixtureKeys(VEHICLES.contains(type) ? VEHICLES : List.of(type)));
            if (!uncovered.isEmpty()) {
                missing.put(type, uncovered);
            }
        });
        assertTrue(missing.isEmpty(), "TXT keys of a content schema with no TXT fixture line (add them to "
                + TxtFixtures.pack() + "): " + missing);
    }

    /** Lower-case keys of every {@code key = value} line in the fixture folders of the given types. */
    private static Set<String> fixtureKeys(List<ContentType> types) {
        Set<String> keys = new TreeSet<>();
        for (ContentType type : types) {
            for (String file : ContentPipeline.contentFiles(TxtFixtures.pack(), type)) {
                for (String line : readLines(TxtFixtures.pack().resolve(file))) {
                    String trimmed = line.trim();
                    if (trimmed.equalsIgnoreCase("endif") || trimmed.equalsIgnoreCase("exit")) {
                        keys.add(trimmed.toLowerCase(Locale.ROOT));
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator > 0 && trimmed.length() > separator + 1) {
                        keys.add(trimmed.substring(0, separator).trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return keys;
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
