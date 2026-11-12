package com.ivomens.mcheli.content.source;

import com.ivomens.mcheli.content.bind.HudTxtReader;
import com.ivomens.mcheli.content.bind.HudYamlReader;
import com.ivomens.mcheli.content.bind.TxtReader;
import com.ivomens.mcheli.content.bind.YamlReader;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableSchema;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.content.def.weapon.WeaponSchema;
import com.ivomens.mcheli.content.emit.HudEmitter;
import com.ivomens.mcheli.content.emit.SchemaEmitter;
import com.ivomens.mcheli.content.pack.AddonResourceLocation;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.content.registry.ContentType;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.core.content.schema.Schema;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * How one {@link ContentType} is read and written: a reader per file format and the YAML emitter. Each type
 * is declared once, here.
 *
 * @param readers the readers by file extension ({@code yml}, {@code txt}); the extension is also stored on the
 *                entry as its format
 * @param emitter writes the definition as YAML
 * @param <D>     the definition the files read into
 */
public record ContentFormat<D extends ContentDefinition>(Map<String, Reader<D>> readers, Function<D, String> emitter) {

    /** The format of entries built in code instead of read from a file. */
    public static final String BUILTIN = "builtin";
    /** The extension, and format, of YAML files. */
    public static final String YAML = "yml";
    /** The extension, and format, of the deprecated legacy TXT files. */
    public static final String TXT = "txt";

    private static final Map<ContentType, ContentFormat<?>> FORMATS = formats();

    /** Reads one file into its definition; throws {@link ContentParseException} for a file it skips. */
    @FunctionalInterface
    public interface Reader<D> {

        /**
         * @param name   the file name without extension
         * @param file   the path inside the pack, for messages
         * @param reload true when an entry is read again in game
         */
        D read(String name, String file, List<String> lines, boolean reload);
    }

    public ContentFormat {
        readers = Map.copyOf(readers);
    }

    /** The format of the content in {@code type}'s folder. */
    public static ContentFormat<?> of(ContentType type) {
        return FORMATS.get(type);
    }

    /** Whether files with this extension are read. */
    public boolean reads(String extension) {
        return this.readers.containsKey(extension);
    }

    /** Reads a file in the format its extension names. */
    public ContentEntry<D> read(ContentType type, AddonResourceLocation location, String file, String extension,
                                List<String> lines, boolean reload) {
        D definition = this.readers.get(extension).read(location.getPath(), file, lines, reload);
        return new ContentEntry<>(type, location, file, extension, definition.author(), definition);
    }

    /** The entry's definition as YAML. */
    public String emit(ContentEntry<?> entry) {
        @SuppressWarnings("unchecked")
        D definition = (D) entry.definition();
        return this.emitter.apply(definition);
    }

    private static Map<ContentType, ContentFormat<?>> formats() {
        Map<ContentType, ContentFormat<?>> formats = new EnumMap<>(ContentType.class);
        formats.put(ContentType.HELICOPTER, vehicle(VehicleKind.HELICOPTER));
        formats.put(ContentType.PLANE, vehicle(VehicleKind.PLANE));
        formats.put(ContentType.SHIP, vehicle(VehicleKind.SHIP));
        formats.put(ContentType.TANK, vehicle(VehicleKind.TANK));
        formats.put(ContentType.VEHICLE, vehicle(VehicleKind.VEHICLE));
        formats.put(ContentType.WEAPON, schema(WeaponSchema.SCHEMA));
        formats.put(ContentType.THROWABLE, schema(ThrowableSchema.SCHEMA));
        formats.put(ContentType.HUD, new ContentFormat<>(Map.of(
                YAML, (name, file, lines, reload) -> HudYamlReader.read(name, file, lines),
                TXT, (name, file, lines, reload) -> HudTxtReader.read(name, file, lines)),
                HudEmitter::emit));
        return Collections.unmodifiableMap(formats);
    }

    /** A settings file read through one schema; YAML files are read whole even on a reload. */
    private static <D extends ContentDefinition> ContentFormat<D> schema(Schema<D> schema) {
        return new ContentFormat<>(Map.of(
                YAML, (name, file, lines, reload) -> YamlReader.read(schema, name, file, lines),
                TXT, (name, file, lines, reload) -> TxtReader.read(schema, name, file, lines, reload)),
                definition -> SchemaEmitter.emit(schema, definition));
    }

    /** A vehicle file, read through its kind's schema and linked to the weapons and HUDs it names. */
    private static ContentFormat<VehicleFile> vehicle(VehicleKind kind) {
        return new ContentFormat<>(Map.of(
                YAML, (name, file, lines, reload) -> VehicleFiles.readYaml(kind, name, file, lines),
                TXT, (name, file, lines, reload) -> VehicleFiles.readTxt(kind, name, file, lines, reload)),
                definition -> SchemaEmitter.emit(VehicleSchema.forKind(kind), definition));
    }
}
