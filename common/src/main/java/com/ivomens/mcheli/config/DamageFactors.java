package com.ivomens.mcheli.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Damage multipliers from the {@code DamageVs*} and {@code Damage*ByExternal} lines. Each line is
 * {@code factor} or {@code factor, name}: a line without a name applies to every hit in its table,
 * a named one only when the entity class name contains the name. Matching lines multiply.
 */
public record DamageFactors(Map<Table, List<Rule>> tables) {

    /** The tables, each read from its own key. */
    public enum Table {
        VS_ENTITY("DamageVsEntity"),
        VS_LIVING("DamageVsLiving"),
        VS_PLAYER("DamageVsPlayer"),
        VS_AIRCRAFT("DamageVsMCHeliAircraft"),
        VS_TANK("DamageVsMCHeliTank"),
        VS_VEHICLE("DamageVsMCHeliVehicle"),
        VS_OTHER("DamageVsMCHeliOther"),
        AIRCRAFT_BY_EXTERNAL("DamageMCHeliAircraftByExternal"),
        TANK_BY_EXTERNAL("DamageMCHeliTankByExternal"),
        VEHICLE_BY_EXTERNAL("DamageMCHeliVehicleByExternal"),
        OTHER_BY_EXTERNAL("DamageMCHeliOtherByExternal");

        public final String key;

        Table(String key) {
            this.key = key;
        }
    }

    /** One line: the factor, and the part of a class name it is limited to (empty for every hit). */
    public record Rule(double factor, String name) {

        /** Empty when the factor is not a number. */
        static Optional<Rule> parse(String text) {
            String[] parts = text.split("\\s*,\\s*");
            try {
                return Optional.of(switch (parts.length) {
                    case 1 -> new Rule(Double.parseDouble(parts[0]), "");
                    case 2 -> new Rule(Double.parseDouble(parts[0]), parts[1]);
                    default -> new Rule(1.0, "");
                });
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        boolean appliesToEveryHit() {
            return this.name.isEmpty();
        }
    }

    /** Neutral catch-all lines, so the written file names every table. */
    private static final String NEUTRAL = "1.0";

    /**
     * Every line of every table in one list, in file order. Without any line in the file, each table
     * gets a neutral line and Flan's Mod bullets get their old balance.
     */
    public static final ListOption OPTION = new ListOption(Arrays.stream(Table.values()).map(t -> t.key).toList(),
            defaultEntries());

    public DamageFactors {
        Map<Table, List<Rule>> copy = new EnumMap<>(Table.class);
        tables.forEach((table, rules) -> copy.put(table, List.copyOf(rules)));
        tables = Map.copyOf(copy);
    }

    static DamageFactors from(List<ListOption.Entry> entries) {
        Map<Table, List<Rule>> tables = new EnumMap<>(Table.class);
        for (Table table : Table.values()) {
            tables.put(table, new ArrayList<>());
        }
        for (ListOption.Entry entry : entries) {
            Table table = table(entry.key());
            Rule.parse(entry.value()).ifPresent(rule -> tables.get(table).add(rule));
        }
        return new DamageFactors(tables);
    }

    /** {@code damage} times every rule of the table that applies, given which names the hit matches. */
    public float apply(Table table, float damage, Predicate<String> matchesName) {
        float result = damage;
        for (Rule rule : this.tables.getOrDefault(table, List.of())) {
            if (rule.appliesToEveryHit() || matchesName.test(rule.name())) {
                result = (float) (result * rule.factor());
            }
        }
        return result;
    }

    /** The entries plus a neutral line for each table that has no catch-all line yet. */
    static List<ListOption.Entry> withCatchAlls(List<ListOption.Entry> entries) {
        List<ListOption.Entry> result = new ArrayList<>(entries);
        for (Table table : Table.values()) {
            boolean hasCatchAll = entries.stream().anyMatch(entry -> entry.key().equals(table.key)
                    && Rule.parse(entry.value()).map(Rule::appliesToEveryHit).orElse(false));
            if (!hasCatchAll) {
                result.add(new ListOption.Entry(table.key, NEUTRAL));
            }
        }
        return List.copyOf(result);
    }

    /** Whether the factor of an entry can be read. */
    static boolean isReadable(ListOption.Entry entry) {
        return Rule.parse(entry.value()).isPresent();
    }

    private static Table table(String key) {
        for (Table table : Table.values()) {
            if (table.key.equals(key)) {
                return table;
            }
        }
        throw new IllegalArgumentException("not a damage table: " + key);
    }

    private static List<ListOption.Entry> defaultEntries() {
        List<ListOption.Entry> entries = new ArrayList<>(withCatchAlls(List.of()));
        entries.add(new ListOption.Entry(Table.VS_ENTITY.key, "3.0, flansmod"));
        entries.add(new ListOption.Entry(Table.AIRCRAFT_BY_EXTERNAL.key, "0.5, flansmod"));
        entries.add(new ListOption.Entry(Table.VEHICLE_BY_EXTERNAL.key, "0.5, flansmod"));
        return entries;
    }
}
