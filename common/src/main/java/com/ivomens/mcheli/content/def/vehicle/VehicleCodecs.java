package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.VehicleFile.RideRack;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.InlineList;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Value spellings only vehicle files use. */
final class VehicleCodecs {

    /** The legacy flare patterns by name; the number is what the flare code uses. */
    private static final Map<String, Integer> FLARE_TYPES = flareTypes();
    private static final String DEFAULT_FUEL = "mch_fuel";

    /** A legacy TXT flag that is on unless it says {@code false}, such as {@code CanRide}. */
    static final ValueCodec<Boolean> ON_UNLESS_FALSE = ValueCodec.of(
            raw -> Codecs.require(raw, Boolean.class, "true/false"),
            text -> !text.equalsIgnoreCase("false"),
            value -> value).shaped(ValueShape.flag());

    /**
     * The creative tab category: text, or a list of names joined with commas, in upper case. Legacy TXT
     * separates the names with commas, semicolons or colons and turns them into dots.
     */
    static final ValueCodec<String> CATEGORY = ValueCodec.of(
            VehicleCodecs::categoryFromYaml,
            text -> text.toUpperCase(Locale.ROOT).replaceAll("[,;:]", ".").replaceAll("[ \t]", ""),
            value -> value).shaped(ValueShape.oneOrMore(ValueShape.text())
            .note("The creative tab category, such as M.A; a list is joined with commas."));

    /** Lines of a crafting pattern, in upper case. */
    static final ValueCodec<List<String>> PATTERN = ValueCodec.of(
            raw -> Codecs.list(raw).stream().map(line -> Codecs.text(line).toUpperCase(Locale.ROOT).trim())
                    .toList(),
            text -> List.of(text.toUpperCase(Locale.ROOT)),
            lines -> lines).shaped(ValueShape.listOf(ValueShape.text()));

    /** The fluids a vehicle burns: one name, or a map of names to how fast each is used up. */
    static final ValueCodec<Map<String, Float>> FUEL_TYPES = ValueCodec.of(
            VehicleCodecs::fuelTypesFromYaml,
            text -> Map.of(text, 1.0F),
            types -> types.size() == 1 && types.containsValue(1.0F) ? types.keySet().iterator().next()
                    : new TreeMap<>(types)).shaped(ValueShape.either(ValueShape.text(),
                    ValueShape.mapOf(ValueShape.number()))
            .note("A fluid name, or a map of fluid names to how fast each is used up."));

    /** A list of names, written with an item per line. */
    static final ValueCodec<List<String>> NAMES = ValueCodec.of(
            raw -> Codecs.list(raw).stream().map(name -> Codecs.text(name).trim()).toList(),
            text -> List.of(text.trim()),
            names -> names).shaped(ValueShape.listOf(ValueShape.text()));

    /** One name, or a list of names; written as {@code Name: x} for one and a list for more. */
    static final ValueCodec<List<String>> ONE_OR_MORE_NAMES = ValueCodec.of(
            VehicleCodecs::namesFromYaml,
            text -> List.of(text.toLowerCase(Locale.ROOT).split("\\s*/\\s*")),
            names -> names.size() == 1 ? names.getFirst() : names).shaped(ValueShape.oneOrMore(ValueShape.text()));

    /** Weapon types separated by {@code /}, in lower case. */
    static final ValueCodec<List<String>> SLASHED_NAMES = ValueCodec.of(
            raw -> slashed(Codecs.text(raw)),
            VehicleCodecs::slashed,
            names -> String.join("/", names)).shaped(ValueShape.text().note("Names separated by '/'."));

    /** The flare patterns: names or legacy numbers, one or a list. */
    static final ValueCodec<List<Integer>> FLARE_PATTERNS = ValueCodec.of(
            VehicleCodecs::flareTypesFromYaml,
            VehicleCodecs::flareTypesFromText,
            VehicleCodecs::flareTypesToYaml).shaped(ValueShape.oneOrMore(ValueShape.either(
                    ValueShape.words(List.copyOf(FLARE_TYPES.keySet())), ValueShape.whole()))
            .note("Flare patterns by name, or legacy numbers."));

    /** {@code Chaff}: false leaves chaff as it was, anything else turns it on. */
    static final ValueCodec<Boolean> CHAFF = new ValueCodec<>() {
        @Override
        public Boolean fromYaml(Object raw, Boolean previous, ReadContext context) {
            return raw instanceof Boolean enabled && !enabled ? previous : Boolean.TRUE;
        }

        @Override
        public Boolean fromText(String text, Boolean previous, ReadContext context) {
            return Boolean.TRUE;
        }

        @Override
        public Object toYaml(Boolean value) {
            return value;
        }

        @Override
        public ValueShape shape() {
            return ValueShape.anything().note("false leaves chaff off; any other value turns it on.");
        }
    };

    /** A legacy TXT key whose presence switches a feature on, whatever its value ({@code HasAPS = no}). */
    static final ValueCodec<Boolean> ON_WHEN_PRESENT = ValueCodec.of(
            raw -> Codecs.require(raw, Boolean.class, "true/false"),
            text -> Boolean.TRUE,
            value -> value).shaped(ValueShape.flag());

    /** A colour as ARGB hex. Legacy TXT reads {@code 0x}, or plain hex digits; a leading {@code #} fails. */
    static final ValueCodec<Integer> HEX_COLOR = ValueCodec.of(
            raw -> (int) Long.decode(hexPrefixed(Codecs.text(raw).trim())).longValue(),
            VehicleCodecs::legacyHex,
            color -> String.format((color >>> 24) == 0 ? "#%06X" : "#%08X", color))
            .shaped(ValueShape.text("^\\s*(#|0[xX])?[0-9A-Fa-f]+\\s*$").note("An ARGB hex colour such as '#FF8800'."));

    /** Tank weight: {@code UNKNOWN}, {@code CAR} or {@code TANK}; TXT words other than car and tank are 0. */
    static final ValueCodec<Integer> TANK_WEIGHT = ValueCodec.of(
            raw -> strictEnum(TankWeight.class, raw, "weight type").ordinal(),
            text -> switch (text.toLowerCase(Locale.ROOT)) {
                case "car" -> TankWeight.CAR.ordinal();
                case "tank" -> TankWeight.TANK.ordinal();
                default -> TankWeight.UNKNOWN.ordinal();
            },
            weight -> TankWeight.values()[weight].name())
            .shaped(ValueShape.words(Arrays.stream(TankWeight.values()).map(Enum::name).toList()));

    /** What moves a turret part: a type name, or its number (legacy TXT allows numbers no type has). */
    static final ValueCodec<Integer> TURRET_PART_TYPE = ValueCodec.of(
            raw -> raw instanceof Number number ? number.intValue()
                    : strictEnum(Parts.TurretPartType.class, raw, "Vpart type").ordinal(),
            Codecs::parseInt,
            type -> type >= 0 && type < Parts.TurretPartType.values().length
                    ? Parts.TurretPartType.values()[type].name() : type)
            .shaped(ValueShape.either(ValueShape.words(Arrays.stream(Parts.TurretPartType.values()).map(Enum::name)
                    .toList()), ValueShape.whole()));

    /**
     * Racks of other vehicles this one rides on, as a map of vehicle names to a rack number, or to a list of rack
     * numbers when it rides on several racks of the same vehicle ({@code c5: [1, 2]}). Racks of one vehicle are
     * kept together, in the order the file names them.
     */
    static final ValueCodec<List<RideRack>> RIDE_RACKS = ValueCodec.of(
            VehicleCodecs::rideRacksFromYaml,
            text -> {
                throw new UnsupportedOperationException("TXT adds ride racks with RideRack lines");
            },
            VehicleCodecs::rideRacksToYaml).shaped(ValueShape.mapOf(ValueShape.oneOrMore(
                    ValueShape.whole(0, Integer.MAX_VALUE)))
            .note("Vehicle names to the rack number this vehicle rides on, or a list of rack numbers."));

    /** Groups of seats, by number, of which only one may be taken at a time. */
    static final ValueCodec<List<List<Integer>>> SEAT_GROUPS = ValueCodec.of(
            raw -> Codecs.list(raw).stream().<List<Integer>>map(group -> Codecs.list(group).stream()
                    .map(seat -> Codecs.number(seat).intValue()).toList()).toList(),
            text -> {
                throw new UnsupportedOperationException("TXT groups seats with ExclusionSeat lines");
            },
            groups -> groups.stream().map(InlineList::new).toList())
            .shaped(ValueShape.listOf(ValueShape.listOf(ValueShape.number())).note("Groups of seat numbers."));

    private VehicleCodecs() {
    }

    /** A radar type name or number; a name the game does not know keeps {@code fallback}, or the previous. */
    static ValueCodec<RadarType> radarType(RadarType fallback) {
        return new EnumCodec<>(RadarType.class, RadarType::parse, fallback);
    }

    static ValueCodec<RWRType> rwrType(RWRType fallback) {
        return new EnumCodec<>(RWRType.class, RWRType::parse, fallback);
    }

    /** Parses with {@code parse(value, fallback)}; a null fallback falls back to the value before. */
    private record EnumCodec<E extends Enum<E>>(Class<E> type, EnumParser<E> parse, E fallback)
            implements ValueCodec<E> {

        @Override
        public E fromYaml(Object raw, E previous, ReadContext context) {
            return this.parse.parse(raw, this.fallback != null ? this.fallback : previous);
        }

        @Override
        public E fromText(String text, E previous, ReadContext context) {
            return this.parse.parse(text, this.fallback != null ? this.fallback : previous);
        }

        @Override
        public Object toYaml(E value) {
            return value.name();
        }

        @Override
        public ValueShape shape() {
            E[] constants = this.type.getEnumConstants();
            return ValueShape.either(ValueShape.suggested(Arrays.stream(constants).map(Enum::name).toList()),
                    ValueShape.whole()).note("A name or its number; a name the game does not know is ignored.");
        }
    }

    @FunctionalInterface
    private interface EnumParser<E> {
        E parse(Object value, E fallback);
    }

    /** How a tank's weight sits; the order is the number the game keeps. */
    enum TankWeight {
        UNKNOWN,
        CAR,
        TANK
    }

    private static String categoryFromYaml(Object raw) {
        if (raw instanceof List<?> names) {
            return names.stream().map(name -> Codecs.text(name).trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.joining(","));
        }
        return Codecs.require(raw, String.class, "a text value or a list").toUpperCase(Locale.ROOT).trim();
    }

    private static Map<String, Float> fuelTypesFromYaml(Object raw) {
        if (raw instanceof String fluid) {
            return Map.of(fluid, 1.0F);
        }
        Map<String, Float> ratios = new TreeMap<>();
        Codecs.require(raw, Map.class, "a fluid name or a map of fluid to ratio").forEach((fluid, ratio) ->
                ratios.put(String.valueOf(fluid), Codecs.number(ratio).floatValue()));
        return ratios;
    }

    static Map<String, Float> defaultFuelTypes() {
        return Map.of(DEFAULT_FUEL, 1.0F);
    }

    private static List<String> namesFromYaml(Object raw) {
        if (raw instanceof String name) {
            return List.of(name);
        }
        return Codecs.list(raw).stream().map(Codecs::text).toList();
    }

    private static List<String> slashed(String text) {
        return List.of(text.toLowerCase(Locale.ROOT).trim().split("\\s*/\\s*"));
    }

    private static List<Integer> flareTypesFromYaml(Object raw) {
        List<?> names = raw instanceof List<?> list ? list : List.of(raw);
        List<Integer> types = new ArrayList<>();
        for (Object name : names) {
            types.add(name instanceof Number number ? number.intValue() : flareType(Codecs.text(name)));
        }
        return types;
    }

    private static int flareType(String name) {
        Integer type = FLARE_TYPES.get(name.toUpperCase(Locale.ROOT).trim());
        if (type == null) {
            throw new ContentValueException("Invalid flare type: " + name + ". Allowed values: "
                    + String.join(", ", FLARE_TYPES.keySet()));
        }
        return type;
    }

    private static List<Integer> flareTypesFromText(String text) {
        return Arrays.stream(Codecs.splitTxt(text)).map(type -> Math.max(1, Math.min(10, Codecs.parseInt(type))))
                .toList();
    }

    private static Object flareTypesToYaml(List<Integer> types) {
        List<Object> written = new ArrayList<>();
        for (int type : types) {
            written.add(FLARE_TYPES.entrySet().stream().filter(entry -> entry.getValue() == type)
                    .<Object>map(Map.Entry::getKey).findFirst().orElse(type));
        }
        return written;
    }

    private static Map<String, Integer> flareTypes() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("NONE", 0);
        types.put("NORMAL", 1);
        types.put("LARGE_AIRCRAFT", 2);
        types.put("SIDE", 3);
        types.put("FRONT", 4);
        types.put("DOWN", 5);
        types.put("SMOKE_LAUNCHER", 10);
        return types;
    }

    private static String hexPrefixed(String hex) {
        if (hex.startsWith("#")) {
            return "0x" + hex.substring(1);
        }
        return hex.startsWith("0x") || hex.startsWith("0X") ? hex : "0x" + hex;
    }

    /** The legacy TXT reading, which treats a {@code #} prefix as part of the digits. */
    private static int legacyHex(String text) {
        try {
            boolean prefixed = text.startsWith("0x") || text.startsWith("0X");
            return (int) Long.decode(prefixed ? text : "0x" + text).longValue();
        } catch (NumberFormatException e) {
            throw new ContentValueException("expected a hex colour such as FFAA00 but got \"" + text + "\"");
        }
    }

    private static <E extends Enum<E>> E strictEnum(Class<E> type, Object raw, String what) {
        String name = Codecs.text(raw).toUpperCase(Locale.ROOT).trim();
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new ContentValueException("Invalid " + what + ": " + raw + ". Allowed values: "
                    + Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    private static List<RideRack> rideRacksFromYaml(Object raw) {
        List<RideRack> racks = new ArrayList<>();
        Codecs.map(raw).forEach((name, value) -> {
            if (name.isEmpty()) {
                throw new ContentValueException("RideRack vehicle name cannot be empty");
            }
            List<?> numbers = value instanceof List<?> ? Codecs.list(value) : Collections.singletonList(value);
            for (Object rack : numbers) {
                int number = Codecs.number(rack).intValue();
                if (number < 0) {
                    throw new ContentValueException("RideRack rack id cannot be negative");
                }
                racks.add(new RideRack(name, number));
            }
        });
        return racks;
    }

    /** A rack number per vehicle, or a list of them for a vehicle with several. */
    private static Object rideRacksToYaml(List<RideRack> racks) {
        Map<String, List<Integer>> byVehicle = new LinkedHashMap<>();
        racks.forEach(rack -> byVehicle.computeIfAbsent(rack.name(), name -> new ArrayList<>()).add(rack.rack()));
        Map<String, Object> map = new LinkedHashMap<>();
        byVehicle.forEach((name, numbers) -> map.put(name, numbers.size() == 1 ? numbers.getFirst()
                : new InlineList(numbers)));
        return map;
    }
}
