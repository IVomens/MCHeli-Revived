package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.config.CombatSettings;
import com.ivomens.mcheli.config.ConfigValues;
import com.ivomens.mcheli.config.ControlSettings;
import com.ivomens.mcheli.config.DebugSettings;
import com.ivomens.mcheli.config.FlightSettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.config.McheliSettings;
import com.ivomens.mcheli.config.Option;
import com.ivomens.mcheli.config.ServerSettings;
import com.ivomens.mcheli.config.VehicleSettings;
import com.ivomens.mcheli.config.wingman.WingmanConfig;
import com.ivomens.mcheli.vehicle.settings.VehicleSettingsSnapshot;

import java.util.List;
import java.util.Map;

/**
 * Pins every setting a recording depends on to a fixed value for the length of the recording, so a
 * changed {@code run/config/mcheli.cfg} or another test cannot change a trajectory.
 * <p>
 * The values are today's defaults. Settings only the vehicle layer reads are not pinned globally:
 * they go into a pinned {@link VehicleSettingsSnapshot} that the harness hands each vehicle it records
 * ({@link #pinVehicle}). Settings other code reads too (weapons, explosions, items, the tank's terrain
 * collision, the definitions' speed multipliers, the server settings) are pinned in the global config;
 * the whole config snapshot and the server settings are saved first and put back afterwards.
 * <p>
 * {@code AllTankSpeed} (and the other speed multipliers where a definition applies them while
 * loading) cannot be pinned this way: the multiplied speed is baked into the definition. The header
 * of every golden file lists the value the definitions were loaded with.
 */
public final class PinnedSettings {

    /**
     * Where a pinned value goes: into the vehicles' snapshot only, or into the global config as well.
     * The pilot settings stay global: {@code Pilot} plays the pilot's client, which reads its config.
     */
    private enum Scope {
        VEHICLE,
        GLOBAL
    }

    /** One config option, its pinned value and where it is pinned. */
    private record Pin<T>(Option<T> option, T value, Scope scope) {

        ConfigValues applyTo(ConfigValues values) {
            return values.with(this.option, this.value);
        }
    }

    /** Config options and their pinned values, in the order they appear in the file header. */
    private static final List<Pin<?>> CONFIG = List.of(
            new Pin<>(FlightSettings.HELI_SPEED, 1.0, Scope.GLOBAL),
            new Pin<>(FlightSettings.PLANE_SPEED, 1.0, Scope.GLOBAL),
            new Pin<>(FlightSettings.SHIP_SPEED, 1.0, Scope.GLOBAL),
            new Pin<>(FlightSettings.TANK_SPEED, 1.0, Scope.GLOBAL),
            new Pin<>(CombatSettings.INFINITY_AMMO, false, Scope.VEHICLE),
            new Pin<>(VehicleSettings.INFINITY_FUEL, false, Scope.VEHICLE),
            new Pin<>(FlightSettings.QUATERNION_ROTATION, true, Scope.VEHICLE),
            new Pin<>(FlightSettings.ENABLE_ROTATION_LIMIT, false, Scope.GLOBAL),
            new Pin<>(ControlSettings.AUTO_THROTTLE_DOWN_HELI, true, Scope.GLOBAL),
            new Pin<>(ControlSettings.AUTO_THROTTLE_DOWN_PLANE, false, Scope.GLOBAL),
            new Pin<>(ControlSettings.AUTO_THROTTLE_DOWN_TANK, false, Scope.GLOBAL),
            new Pin<>(VehicleSettings.DISMOUNT_ALL, false, Scope.GLOBAL),
            new Pin<>(VehicleSettings.FIX_VEHICLE_AT_PLACED_POINT, true, Scope.GLOBAL),
            new Pin<>(CombatSettings.COLLISION_DESTROY_BLOCK, true, Scope.GLOBAL),
            new Pin<>(CombatSettings.COLLISION_DAMAGE, true, Scope.VEHICLE),
            new Pin<>(CombatSettings.COLLISION_DAMAGE_FACTOR, 1.0, Scope.VEHICLE),
            new Pin<>(CombatSettings.COLLISION_ENTITY_DAMAGE, true, Scope.GLOBAL),
            new Pin<>(CombatSettings.COLLISION_ENTITY_TANK_DAMAGE, false, Scope.GLOBAL),
            new Pin<>(CombatSettings.EXPLOSION_DESTROY_BLOCK, true, Scope.GLOBAL),
            new Pin<>(CombatSettings.EXPLOSION_FLAMING_BLOCK, true, Scope.GLOBAL),
            new Pin<>(CombatSettings.HURT_RESISTANT_TIME, 0.0, Scope.GLOBAL),
            new Pin<>(VehicleSettings.AUTO_REPAIR_HP, 0.5, Scope.VEHICLE),
            new Pin<>(VehicleSettings.DESPAWN_COUNT, 25, Scope.GLOBAL),
            new Pin<>(VehicleSettings.KILL_PASSENGERS_WHEN_DESTROYED, false, Scope.VEHICLE),
            new Pin<>(VehicleSettings.PREVENTING_BROKEN, false, Scope.VEHICLE),
            new Pin<>(VehicleSettings.ITEM_FUEL, true, Scope.GLOBAL),
            new Pin<>(VehicleSettings.ITEM_DAMAGE, true, Scope.GLOBAL),
            new Pin<>(VehicleSettings.ENABLE_PUT_RACK_IN_FLYING, true, Scope.VEHICLE),
            new Pin<>(DebugSettings.TEST_MODE, false, Scope.GLOBAL),
            // read by the attitude code the harness runs in place of the pilot's client
            new Pin<>(ControlSettings.FLIGHT_SIM_MODE, false, Scope.VEHICLE),
            new Pin<>(FlightSettings.FIXED_STEP_ATTITUDE, false, Scope.VEHICLE),
            new Pin<>(FlightSettings.SERVER_TERRAIN_FOLLOW, false, Scope.GLOBAL));

    private static final boolean ROTATION_LIMIT = false;
    private static final float PITCH_LIMIT_MIN = -10.0F;
    private static final float PITCH_LIMIT_MAX = 10.0F;
    private static final float ROLL_LIMIT = 35.0F;
    private static final boolean PVP = true;
    private static final double STINGER_LOCK_RANGE = 120.0;
    private static final boolean LIVERIES = true;

    /** The pinned server settings, by field name, in the order they appear in the file header. */
    private static final List<Map.Entry<String, Object>> SERVER = List.of(
            Map.entry("enableRotationLimit", ROTATION_LIMIT),
            Map.entry("pitchLimitMin", PITCH_LIMIT_MIN),
            Map.entry("pitchLimitMax", PITCH_LIMIT_MAX),
            Map.entry("rollLimit", ROLL_LIMIT),
            Map.entry("enablePVP", PVP),
            Map.entry("stingerLockRange", STINGER_LOCK_RANGE),
            Map.entry("enableLiveries", LIVERIES));

    private final McheliSettings savedConfig;
    private final ServerSettings savedServer;
    private VehicleSettingsSnapshot vehicleSettings;

    private PinnedSettings(McheliSettings savedConfig, ServerSettings savedServer) {
        this.savedConfig = savedConfig;
        this.savedServer = savedServer;
    }

    /** Saves all settings and applies the pinned values; call {@link #restore()} when done. */
    public static PinnedSettings pin() {
        return pin(false);
    }

    /**
     * Saves all settings and applies the pinned values; call {@link #restore()} when done.
     *
     * @param fixedSteps turns {@code FixedStepAttitude} on, for the 60 FPS cases
     */
    public static PinnedSettings pin(boolean fixedSteps) {
        PinnedSettings settings = new PinnedSettings(McheliConfig.get(), McheliConfig.server());
        ConfigValues global = settings.savedConfig.values();
        ConfigValues all = global;
        for (Pin<?> pin : CONFIG) {
            all = pin.applyTo(all);
            if (pin.scope() == Scope.GLOBAL) {
                global = pin.applyTo(global);
            }
        }
        ServerSettings server = pinServer(settings.savedServer);
        McheliConfig.set(McheliSettings.from(global));
        McheliConfig.applyServerSettings(server);
        settings.vehicleSettings = VehicleSettingsSnapshot.of(
                McheliSettings.from(all.with(FlightSettings.FIXED_STEP_ATTITUDE, fixedSteps)), server,
                WingmanConfig.get());
        return settings;
    }

    /** Hands the vehicle the pinned snapshot; call it before the vehicle's first tick. */
    public void pinVehicle(AircraftEntity vehicle) {
        vehicle.pinSettings(this.vehicleSettings);
    }

    public void restore() {
        McheliConfig.set(this.savedConfig);
        McheliConfig.applyServerSettings(this.savedServer);
    }

    /** The pinned values as a JSON object for the file header. */
    public static String describe() {
        StringBuilder json = new StringBuilder("{");
        CONFIG.forEach(pin -> appendJson(json, pin.option().key(), pin.value()));
        SERVER.forEach(entry -> appendJson(json, "server." + entry.getKey(), entry.getValue()));
        return json.append('}').toString();
    }

    private static ServerSettings pinServer(ServerSettings current) {
        return new ServerSettings(current.enableCamDistChange(), current.enableEntityMarker(), PVP,
                STINGER_LOCK_RANGE, current.enableDebugBoundingBox(), ROTATION_LIMIT, PITCH_LIMIT_MAX, PITCH_LIMIT_MIN,
                ROLL_LIMIT, LIVERIES);
    }

    private static void appendJson(StringBuilder json, String name, Object value) {
        if (json.length() > 1) {
            json.append(',');
        }
        json.append('"').append(name).append("\":").append(value);
    }
}
