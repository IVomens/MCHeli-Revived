package com.ivomens.mcheli.golden.content;

import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes every field of a content object, recursively, as sorted {@code path = value} lines.
 * <p>
 * The dump survived the rewrite of the info classes: the content types they adapted are dumped through
 * {@link LegacyViews}, which lists today's values under the old field names, and the run-time part classes
 * that moved are written under their old class names ({@link #LEGACY_CLASS_NAMES}). Any value that changed
 * shows up as a one-line diff. Floats and doubles use {@link Float#toString}/{@link Double#toString}, so the
 * text round-trips exactly. Collections without an order (hash sets and maps) are sorted.
 * <p>
 * Objects of our own classes are walked field by field; Minecraft and JDK value types are written
 * with a stable text form. Anything else is written as its class name, so an identity hash never
 * makes a dump differ between runs.
 */
public final class InfoDump {

    private static final String OWN_PACKAGE = "com.ivomens.mcheli.";
    private static final String AIRCRAFT_INFO = "com.ivomens.mcheli.aircraft.AircraftInfo$";
    /** Run-time classes of the content rewrite, by the class they were before it. */
    private static final Map<String, String> LEGACY_CLASS_NAMES = legacyClassNames();
    /** Enums renamed by the content rewrite, by their old simple name. */
    private static final Map<String, String> LEGACY_ENUM_NAMES = Map.of("HitBoxType", "EnumBoundingBoxType");

    private final Map<String, String> lines = new TreeMap<>(InfoDump::comparePaths);
    /** Objects on the path being written, to cut reference cycles (a part pointing back at its info). */
    private final Set<Object> path = Collections.newSetFromMap(new IdentityHashMap<>());

    private InfoDump() {
    }

    /** The dump of one object: one {@code path = value} line per leaf, sorted by path. */
    public static String of(Object root) {
        InfoDump dump = new InfoDump();
        dump.write("", root);
        StringBuilder text = new StringBuilder();
        dump.lines.forEach((key, value) -> text.append(key).append(" = ").append(value).append('\n'));
        return text.toString();
    }

    private void write(String name, Object value) {
        if (value == null) {
            this.lines.put(name, "null");
            return;
        }
        String simple = simpleValue(value);
        if (simple != null) {
            this.lines.put(name, simple);
            return;
        }
        if (!this.path.add(value)) {
            this.lines.put(name, "<cycle " + value.getClass().getSimpleName() + ">");
            return;
        }
        try {
            writeComposite(name, value);
        } finally {
            this.path.remove(value);
        }
    }

    private void writeComposite(String name, Object value) {
        if (value.getClass().isArray()) {
            writeElements(name, arrayElements(value));
        } else if (value instanceof List<?> list) {
            writeElements(name, list);
        } else if (value instanceof Collection<?> collection) {
            writeElements(name, sortedByText(collection));
        } else if (value instanceof Map<?, ?> map) {
            writeMap(name, map);
        } else if (value instanceof LegacyObject legacy) {
            writeLegacy(name, legacy);
        } else if (value.getClass().getName().startsWith(OWN_PACKAGE)) {
            writeFields(name, value);
        } else {
            this.lines.put(name, "<" + value.getClass().getName() + ">");
        }
    }

    private void writeElements(String name, List<?> elements) {
        this.lines.put(name + ".size", Integer.toString(elements.size()));
        for (int i = 0; i < elements.size(); i++) {
            write(name + "[" + i + "]", elements.get(i));
        }
    }

    private void writeMap(String name, Map<?, ?> map) {
        this.lines.put(name + ".size", Integer.toString(map.size()));
        Map<String, Object> sorted = new TreeMap<>();
        map.forEach((key, value) -> sorted.put(String.valueOf(simpleOrClass(key)), value));
        sorted.forEach((key, value) -> write(name + "{" + key + "}", value));
    }

    private void writeFields(String name, Object value) {
        String prefix = name.isEmpty() ? "" : name + ".";
        String className = value.getClass().getName();
        this.lines.put(prefix + "@class", LEGACY_CLASS_NAMES.getOrDefault(className, className));
        for (Field field : instanceFields(value.getClass())) {
            write(prefix + field.getName(), read(field, value));
        }
    }

    private void writeLegacy(String name, LegacyObject legacy) {
        String prefix = name.isEmpty() ? "" : name + ".";
        this.lines.put(prefix + "@class", legacy.className());
        legacy.fields().forEach((field, value) -> write(prefix + field, value));
        if (legacy.rawLines() != null) {
            legacy.rawLines().write(prefix, this.lines::put);
        }
    }

    private static Map<String, String> legacyClassNames() {
        Map<String, String> names = new TreeMap<>();
        for (String part : List.of("Camera", "CameraPosition", "Canopy", "Chaff", "CrawlerTrack", "CrawlerTrackPrm",
                "DrawnPart", "Flare", "Hatch", "LandingGear", "PartWeapon", "PartWeaponChild", "PartWheel",
                "ParticleSplash", "RepellingHook", "RideRack", "RotPart", "SearchLight", "Throttle", "TrackRoller",
                "Weapon", "WeaponBay", "WeaponSet", "Wheel")) {
            names.put("com.ivomens.mcheli.aircraft.VehicleParts$" + part, AIRCRAFT_INFO + part);
        }
        names.put("com.ivomens.mcheli.aircraft.VehicleParts$HeliRotor", "com.ivomens.mcheli.helicopter.HelicopterInfo$Rotor");
        names.put("com.ivomens.mcheli.aircraft.VehicleParts$Propeller", "com.ivomens.mcheli.plane.AbstractPlaneInfo$Rotor");
        for (String part : List.of("Blade", "Pylon", "Wing")) {
            names.put("com.ivomens.mcheli.aircraft.VehicleParts$" + part, "com.ivomens.mcheli.plane.AbstractPlaneInfo$" + part);
        }
        names.put("com.ivomens.mcheli.aircraft.VehicleParts$ExhaustFlame", "com.ivomens.mcheli.plane.PlaneInfo$ExhaustFlame");
        names.put("com.ivomens.mcheli.aircraft.VehicleParts$VPart", "com.ivomens.mcheli.vehicle.VehicleInfo$VPart");
        names.put("com.ivomens.mcheli.combat.weapon.WeaponSpec$MuzzleFlash", "com.ivomens.mcheli.combat.weapon.WeaponInfo$MuzzleFlash");
        return Map.copyOf(names);
    }

    /**
     * Non-static, non-synthetic fields of the class and its superclasses. Transient fields are run-time
     * plumbing (listeners), not content, and are left out.
     */
    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())
                        && !field.isSynthetic()) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        // A field hidden by a subclass field of the same name keeps both; the owner tells them apart.
        fields.sort(Comparator.comparing(Field::getName));
        return fields;
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A stable one-line form for value types, or null for objects that need walking. */
    private static String simpleValue(Object value) {
        return switch (value) {
            case String s -> quote(s);
            case Float f -> Float.toString(f);
            case Double d -> Double.toString(d);
            case Number n -> n.toString();
            case Boolean b -> b.toString();
            case Character c -> quote(c.toString());
            case Enum<?> e -> LEGACY_ENUM_NAMES.getOrDefault(e.getDeclaringClass().getSimpleName(),
                    e.getDeclaringClass().getSimpleName()) + "." + e.name();
            case LegacyObject.Text text -> text.text();
            case Class<?> c -> "class " + c.getName();
            case ResourceLocation location -> location.toString();
            case ResourceKey<?> key -> key.location().toString();
            case Holder<?> holder -> holder.unwrapKey().map(key -> key.location().toString()).orElse("<direct holder>");
            case SoundEvent sound -> "sound " + sound.getLocation();
            case Item item -> "item " + BuiltInRegistries.ITEM.getKey(item);
            case Block block -> "block " + BuiltInRegistries.BLOCK.getKey(block);
            case MobEffectInstance effect -> effect.toString();
            case Vec3 v -> "(" + v.x + ", " + v.y + ", " + v.z + ")";
            case Vec2 v -> "(" + v.x + ", " + v.y + ")";
            case Vec3i v -> "(" + v.getX() + ", " + v.getY() + ", " + v.getZ() + ")";
            case Vector3f v -> "(" + v.x + ", " + v.y + ", " + v.z + ")";
            case ItemStack stack -> stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem())
                    + (stack.getComponentsPatch().isEmpty() ? "" : " " + stack.getComponentsPatch());
            case Quaternionf q -> "(" + q.x + ", " + q.y + ", " + q.z + ", " + q.w + ")";
            case AABB box -> "[" + box.minX + ", " + box.minY + ", " + box.minZ + " -> "
                    + box.maxX + ", " + box.maxY + ", " + box.maxZ + "]";
            default -> null;
        };
    }

    private static Object simpleOrClass(Object value) {
        String simple = value == null ? "null" : simpleValue(value);
        return simple != null ? simple : value.getClass().getName();
    }

    static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }

    private static List<Object> arrayElements(Object array) {
        int length = Array.getLength(array);
        List<Object> elements = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            elements.add(Array.get(array, i));
        }
        return elements;
    }

    private static List<Object> sortedByText(Collection<?> collection) {
        List<Object> elements = new ArrayList<>(collection);
        elements.sort(Comparator.comparing(element -> String.valueOf(simpleOrClass(element))));
        return elements;
    }

    /** Orders paths so that list indices sort numerically ({@code [2]} before {@code [10]}). */
    private static int comparePaths(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int endA = digitsEnd(a, i);
                int endB = digitsEnd(b, j);
                int byNumber = Long.compare(Long.parseLong(a.substring(i, endA)), Long.parseLong(b.substring(j, endB)));
                if (byNumber != 0) {
                    return byNumber;
                }
                i = endA;
                j = endB;
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        int byLength = Integer.compare(a.length() - i, b.length() - j);
        return byLength != 0 ? byLength : a.compareTo(b);
    }

    private static int digitsEnd(String s, int start) {
        int end = start;
        while (end < s.length() && Character.isDigit(s.charAt(end)) && end - start < 18) {
            end++;
        }
        return end;
    }
}
