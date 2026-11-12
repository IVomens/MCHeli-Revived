package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.hud.HudVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The formula variables of a scripted HUD, in the order and with the Java types
 * {@link HudVariable} declares them. The types matter: Aviator does integer
 * arithmetic on Integer and Long operands, and some variables are booleans that old scripts compare
 * with numbers.
 * <p>
 * Each variable has a small pool of values that matter for real scripts (zero, limits, typical
 * flight values, signs). Frames pick from the pools with a fixed seed, so the grid is the same on
 * every machine and in every later implementation.
 */
final class HudVariables {

    /** One variable: its name and the values a frame may give it, all of the same Java type. */
    record Variable(String name, List<Object> pool) {
    }

    static final List<Variable> ALL = List.of(
            doubles("color", 0.0),
            doubles("center_x", 213.5, 320.0),
            doubles("center_y", 120.0, 180.0),
            doubles("width", 427.0, 640.0),
            doubles("height", 240.0, 360.0),
            longs("time", 0L, 6000L, 18000L, 23999L),
            bools("test_mode"),
            floats("plyr_yaw", -179.5F, -90.0F, 0.0F, 45.25F, 179.9F),
            floats("plyr_pitch", -90.0F, -12.5F, 0.0F, 30.0F, 90.0F),
            floats("brl_yaw", -170.0F, -3.5F, 0.0F, 60.0F),
            floats("brl_pitch", -45.0F, 0.0F, 7.25F, 80.0F),
            floats("yaw", -180.0F, -45.5F, 0.0F, 90.0F, 135.75F),
            floats("pitch", -60.0F, -5.25F, 0.0F, 10.0F, 80.0F),
            floats("roll", -170.0F, -30.0F, 0.0F, 15.5F, 90.0F),
            ints("altitude", 0, 3, 10, 11, 57, 250, 1000),
            doubles("sea_alt", 0.0, 12.75, 200.5),
            bools("have_radar"),
            floats("radar_rot", 0.0F, 90.5F, 359.0F),
            ints("hp", 0, 1, 37, 120, 400),
            ints("max_hp", 1, 120, 400),
            doubles("hp_rto"),
            doubles("hp_per"),
            doubles("throttle", 0.0, 0.35, 0.8, 1.0),
            doubles("pos_x", -1234.5, 0.0, 64.0, 8765.25),
            doubles("pos_y", -60.0, 63.0, 120.5, 320.0),
            doubles("pos_z", -1234.5, 0.0, 64.0, 8765.25),
            doubles("motion_x", -1.5, -0.02, 0.0, 0.3, 2.75),
            doubles("motion_y", -1.5, -0.02, 0.0, 0.3, 2.75),
            doubles("motion_z", -1.5, -0.02, 0.0, 0.3, 2.75),
            doubles("speed"),
            floats("fuel", 0.0F, 0.05F, 0.5F, 1.0F),
            ints("low_fuel", 0, 1),
            doubles("stick_x", -1.0, -0.25, 0.0, 0.5, 1.0),
            doubles("stick_y", -1.0, -0.25, 0.0, 0.5, 1.0),
            bools("reloading"),
            floats("reload_time", 0.0F, 0.3F, 1.0F),
            doubles("wpn_heat", 0.0, 0.5, 1.0),
            bools("is_heat_wpn"),
            ints("sight_type", 0, 1, 2),
            doubles("lock", 0.0, 0.4, 1.0),
            bools("dsp_mt_dist"),
            floats("mt_dist", 0.0F, 120.5F),
            ints("has_airburst", 0, 1),
            ints("vtol_stat", 0, 1, 2),
            ints("free_look", 0, 1),
            bools("gunner_mode"),
            ints("cam_mode", 0, 1, 2),
            floats("cam_zoom", 1.0F, 2.5F, 10.0F),
            ints("auto_pilot", 0, 1),
            bools("have_flare"),
            bools("can_flare"),
            ints("inventory", 0, 9, 27),
            bools("hovering"),
            bools("is_uav"),
            doubles("uav_fs", 0.0, 0.005, 0.5, 1.0),
            doubles("uav_dist", 0.0, 350.5),
            doubles("tvm_diff", 0.0, 25.5),
            ints("msl_heat", 0, 1, 2),
            ints("msl_radar", 0, 1),
            ints("msl_count", 0, 1, 3));

    static final long SEED = 20261106L;
    /**
     * The variables before {@code msl_heat} were all there when the corpus was recorded. Those added since pick
     * from a random stream of their own, so adding one leaves the values of the others as recorded.
     */
    private static final int FIRST_ADDED = indexOf("msl_heat");

    private HudVariables() {
    }

    /**
     * {@code count} frames of variable values. Frame 0 takes the first value of every pool, the rest
     * pick at random with {@link #SEED}. The derived values (health ratio, speed) are computed from
     * the others the way the game computes them.
     */
    static List<Map<String, Object>> frames(int count) {
        Random random = new Random(SEED);
        Random addedRandom = new Random(SEED + 1);
        List<Map<String, Object>> frames = new ArrayList<>();
        for (int frame = 0; frame < count; frame++) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < ALL.size(); i++) {
                Variable variable = ALL.get(i);
                List<Object> pool = variable.pool();
                Random from = i < FIRST_ADDED ? random : addedRandom;
                values.put(variable.name(), pool.isEmpty() ? null
                        : pool.get(frame == 0 ? 0 : from.nextInt(pool.size())));
            }
            derive(values);
            frames.add(values);
        }
        return frames;
    }

    /** Fills in hp_rto, hp_per and speed like {@code HudTelemetry}. */
    static void derive(Map<String, Object> values) {
        int hp = (Integer) values.get("hp");
        int maxHp = (Integer) values.get("max_hp");
        values.put("hp_rto", maxHp > 0 ? (double) hp / maxHp : 0.0);
        values.put("hp_per", maxHp > 0 ? 100.0 * hp / maxHp : 0.0);
        double mx = (Double) values.get("motion_x");
        double my = (Double) values.get("motion_y");
        double mz = (Double) values.get("motion_z");
        values.put("speed", Math.sqrt(mx * mx + my * my + mz * mz));
    }

    /** One line per variable and frame, for the golden header file. */
    static String describe(List<Map<String, Object>> frames) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < frames.size(); i++) {
            text.append("frame ").append(i).append(':');
            frames.get(i).forEach((name, value) -> text.append(' ').append(name).append('=').append(typed(value)));
            text.append('\n');
        }
        return text.toString();
    }

    /** A value with a type suffix: 3 (int), 3L, 3.0F, 3.0D, true. */
    static String typed(Object value) {
        return switch (value) {
            case Integer i -> i.toString();
            case Long l -> l + "L";
            case Float f -> f + "F";
            case Double d -> d + "D";
            case null -> "null";
            default -> value.toString();
        };
    }

    private static int indexOf(String name) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static Variable ints(String name, Integer... values) {
        return new Variable(name, List.of((Object[]) values));
    }

    private static Variable longs(String name, Long... values) {
        return new Variable(name, List.of((Object[]) values));
    }

    private static Variable floats(String name, Float... values) {
        return new Variable(name, List.of((Object[]) values));
    }

    private static Variable doubles(String name, Double... values) {
        return new Variable(name, List.of((Object[]) values));
    }

    private static Variable bools(String name) {
        return new Variable(name, List.of(false, true));
    }
}
