package com.ivomens.mcheli.config;

import com.ivomens.mcheli.util.ModLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.ivomens.mcheli.config.Layout.blank;
import static com.ivomens.mcheli.config.Layout.entry;
import static com.ivomens.mcheli.config.Layout.text;

/**
 * The layout of {@code config/mcheli.cfg}. Keys, order, comments and blank lines are those of every
 * earlier version, so existing files keep loading and a rewritten file only differs where a value
 * was missing or out of range.
 */
public final class McheliConfigFile {

    private final ConfigFile file;

    /** @param modVersion written as {@code MOD_Version}; gates options on the next read */
    public McheliConfigFile(String modVersion) {
        this.file = new ConfigFile(layout(modVersion), List.of(DebugSettings.DEBUG_LOG), true, false);
    }

    /** Reads the file, logging every value that could not be used, and fills in the derived lines. */
    public McheliSettings read(Path path) throws IOException {
        ConfigValues values = this.file.read(path);
        values.get(DamageFactors.OPTION).stream().filter(entry -> !DamageFactors.isReadable(entry))
                .forEach(entry -> ModLogger.warn("{}: {} = {} is not a damage factor; ignoring it",
                        path.getFileName(), entry.key(), entry.value()));
        if (values.isSet(DamageFactors.OPTION)) {
            values = values.with(DamageFactors.OPTION, DamageFactors.withCatchAlls(values.get(DamageFactors.OPTION)));
        }
        return McheliSettings.from(values);
    }

    public void write(Path path, McheliSettings settings) throws IOException {
        this.file.write(path, settings.values());
    }

    public List<String> render(McheliSettings settings) {
        return this.file.render(settings.values());
    }

    private static List<Layout> layout(String modVersion) {
        List<Layout> layout = new ArrayList<>();
        layout.add(text("[General]"));
        layout.add(text("MOD_Name = mcheli"));
        layout.add(text("MOD_Version = " + modVersion));
        layout.add(text("MOD_MC_Version = 1.21.1"));
        layout.add(blank());
        layout.add(new Layout.When(values -> values.get(DebugSettings.DEBUG_LOG),
                List.of(text(DebugSettings.DEBUG_LOG.key()), blank())));
        entries(layout, List.of(DebugSettings.TEST_MODE, DebugSettings.TEXTURE_ALPHA, CommandSettings.ENABLED,
                CommandSettings.CLIENT_BENCHMARK, LoadingSettings.EXTRACT_DEFAULT_CONTENT_PACK));
        layout.add(blank());
        entries(layout, gameplayOptions());
        layout.add(blank());
        entries(layout, clientOptions());
        entries(layout, adminOptions());
        layout.add(blank());
        layout.add(blank());
        layout.add(entry(DamageFactors.OPTION));
        layout.add(blank());
        layout.add(entry(CombatSettings.IGNORED_BULLET_HITS));
        layout.add(blank());
        layout.add(text(";CommandPermission = commandName(eg, modlist, status, fill...):playerName1, playerName2,"
                + " playerName3..."));
        layout.add(new Layout.When(values -> values.get(CommandSettings.PERMISSIONS).isEmpty(),
                List.of(text(";CommandPermission = modlist :example1, example2"),
                        text(";CommandPermission = status :  example2"))));
        layout.add(entry(CommandSettings.PERMISSIONS));
        layout.add(blank());
        layout.add(blank());
        layout.add(text("[Key config]"));
        layout.add(text("https://minecraft.gamepedia.com/Key_codes"));
        layout.add(blank());
        KeySettings.ALL.forEach(option -> layout.add(entry(option)));
        layout.add(blank());
        layout.add(blank());
        layout.add(text("[Handling]"));
        entries(layout, HandlingSettings.ALL);
        layout.add(blank());
        layout.add(blank());
        layout.add(text("[Joystick]"));
        entries(layout, JoystickSettings.ALL);
        return layout;
    }

    private static void entries(List<Layout> layout, List<Option<?>> options) {
        options.forEach(option -> layout.add(entry(option)));
    }

    /** The first group of {@code [General]}: mostly server rules, in file order. */
    private static List<Option<?>> gameplayOptions() {
        return List.of(VehicleSettings.PLACEABLE_ON_SPONGE_ONLY, VehicleSettings.ITEM_DAMAGE,
                VehicleSettings.ITEM_FUEL, VehicleSettings.AUTO_REPAIR_HP, CombatSettings.EXPLOSION_DESTROY_BLOCK,
                CombatSettings.EXPLOSION_FLAMING_BLOCK, BreakRules.BULLET_BREAKABLE_BLOCKS,
                CombatSettings.COLLISION_DESTROY_BLOCK, BreakRules.CAR_BREAKABLE_BLOCKS,
                BreakRules.CAR_BREAKABLE_MATERIALS, BreakRules.TANK_BREAKABLE_BLOCKS,
                BreakRules.TANK_BREAKABLE_MATERIALS, CombatSettings.COLLISION_ENTITY_DAMAGE,
                CombatSettings.COLLISION_ENTITY_TANK_DAMAGE, CombatSettings.INFINITY_AMMO,
                VehicleSettings.INFINITY_FUEL, EnergySettings.SYSTEM, EnergySettings.FE_PER_EU,
                EnergySettings.FE_PER_HE, EnergySettings.CHARGER_RANGE, EnergySettings.CHARGER_TRANSFER_RATE,
                EnergySettings.CHARGER_CAPACITY, VehicleSettings.DISMOUNT_ALL, VehicleSettings.MOUNT_MINECART_HELI,
                VehicleSettings.MOUNT_MINECART_PLANE, VehicleSettings.MOUNT_MINECART_VEHICLE,
                VehicleSettings.MOUNT_MINECART_TANK, VehicleSettings.PREVENTING_BROKEN,
                VehicleSettings.DROP_ITEM_IN_CREATIVE_MODE, VehicleSettings.BREAKABLE_ONLY_PICKAXE,
                FlightSettings.HELI_SPEED, FlightSettings.PLANE_SPEED, FlightSettings.SHIP_SPEED,
                FlightSettings.TANK_SPEED, CombatSettings.HURT_RESISTANT_TIME, CombatSettings.STINGER_LOCK_RANGE,
                CombatSettings.RANGE_FINDER_SPOT_DIST, CombatSettings.RANGE_FINDER_SPOT_TIME,
                CombatSettings.RANGE_FINDER_CONSUME, VehicleSettings.ENABLE_PUT_RACK_IN_FLYING,
                DebugSettings.DEBUG_BOUNDING_BOX, VehicleSettings.DESPAWN_COUNT, DebugSettings.HIT_BOX_DELAY_TICK,
                FlightSettings.ENABLE_ROTATION_LIMIT, FlightSettings.PITCH_LIMIT_MAX, FlightSettings.PITCH_LIMIT_MIN,
                FlightSettings.ROLL_LIMIT, CombatSettings.GUNNER_VS_MONSTER_HORIZONTAL,
                CombatSettings.GUNNER_VS_MONSTER_VERTICAL, CombatSettings.GUNNER_VS_PLAYER_HORIZONTAL,
                CombatSettings.GUNNER_VS_PLAYER_VERTICAL, VehicleSettings.FIX_VEHICLE_AT_PLACED_POINT,
                VehicleSettings.KILL_PASSENGERS_WHEN_DESTROYED);
    }

    /** Added after the second group: ownership, locking, limits and the admin commands. */
    private static List<Option<?>> adminOptions() {
        return List.of(AdminSettings.VEHICLE_LOCKING, AdminSettings.LOCK_ALLOWS_TEAM,
                AdminSettings.LOCK_PASSENGER_SEATS, AdminSettings.MAX_PER_PLAYER, AdminSettings.MAX_PER_CHUNK,
                AdminSettings.MAX_PER_LEVEL, AdminSettings.MAX_HELICOPTERS, AdminSettings.MAX_PLANES,
                AdminSettings.MAX_SHIPS, AdminSettings.MAX_TANKS, AdminSettings.MAX_GROUND_VEHICLES,
                AdminSettings.OPS_BYPASS_LIMITS, AdminSettings.REMOVE_DROPS_ITEM, AdminSettings.COMMAND_PERMISSION);
    }

    /** The second group of {@code [General]}: mostly client options and later additions, in file order. */
    private static List<Option<?>> clientOptions() {
        return List.of(ControlSettings.INVERT_MOUSE, ControlSettings.MOUSE_SENSITIVITY,
                ControlSettings.STICK_MODE_HELI, ControlSettings.STICK_MODE_PLANE, ControlSettings.FLIGHT_SIM_MODE,
                ControlSettings.AUTO_THROTTLE_DOWN_HELI, ControlSettings.AUTO_THROTTLE_DOWN_PLANE,
                ControlSettings.AUTO_THROTTLE_DOWN_TANK, ControlSettings.SWITCH_WEAPON_WITH_MOUSE_WHEEL,
                ControlSettings.LIGHT_WEAPON_AUTO_FIRE, RenderSettings.DISABLE_ITEM_RENDER, RenderSettings.HIDE_KEYBIND,
                RenderSettings.RENDER_DISTANCE_WEIGHT, RenderSettings.MOB_RENDER_DISTANCE_WEIGHT,
                CreativeTabSettings.ITEMS, CreativeTabSettings.HELICOPTERS, CreativeTabSettings.PLANES,
                CreativeTabSettings.SHIPS, CreativeTabSettings.TANKS, CreativeTabSettings.VEHICLES,
                RenderSettings.DISABLE_SHADER, RenderSettings.DEFAULT_EXPLOSION_PARTICLE,
                RenderSettings.ALIVE_TIME_OF_CARTRIDGE, RenderSettings.HIT_MARK_COLOR, RenderSettings.SMOOTH_SHADING,
                RenderSettings.ENABLE_MOD_ENTITY_RENDER, RenderSettings.DISABLE_RENDER_LIVING_SPECIALS,
                HudSettings.DISPLAY_IN_THIRD_PERSON, HudSettings.AUTO_SCALE_AIRCRAFT_GUI,
                FlightSettings.QUATERNION_ROTATION, FlightSettings.FIXED_STEP_ATTITUDE,
                FlightSettings.SERVER_TERRAIN_FOLLOW, LoadingSettings.REMOVE_CLIENT_TRACKING_RESTRICTIONS,
                LoadingSettings.ASYNC_ON_DEMAND_MODEL_LOADING, LoadingSettings.ON_DEMAND_MODEL_LIFETIME_SECONDS,
                LoadingSettings.ON_DEMAND_MODEL_LOADER_THREADS, ControlSettings.DISABLE_CAMERA_DIST_CHANGE,
                RenderSettings.ENABLE_REPLACE_TEXTURE_MANAGER, RenderSettings.DISPLAY_ENTITY_MARKER,
                RenderSettings.ENTITY_MARKER_SIZE, RenderSettings.BLOCK_MARKER_SIZE,
                RenderSettings.REPLACE_RENDER_VIEW_ENTITY, UavSettings.MAX_PAIRED_PER_STATION,
                UavSettings.TABLET_CONTROLS_NORMAL_UAV, NetworkSettings.PROJECTILE_CHUNK_LOAD,
                NetworkSettings.PROJECTILE_CHUNK_LEASE_TICKS, NetworkSettings.FROZEN_PROJECTILE_LIFETIME,
                CombatSettings.COLLISION_DAMAGE,
                CombatSettings.COLLISION_DAMAGE_FACTOR, VehicleSettings.SOUND_RANGE, HudSettings.STYLE,
                HudSettings.COLOR, HudSettings.SCALE, RenderSettings.PARTICLE_STYLE, ControlSettings.INVERT_MOUSE_PITCH,
                LiverySettings.ENABLED, LiverySettings.UPLOAD_OPERATORS_ONLY, LiverySettings.MAX_KILOBYTES,
                LiverySettings.MAX_DIMENSION, LiverySettings.PER_PLAYER, LiverySettings.STORAGE_MEGABYTES,
                UavSettings.CHUNK_LOADING, UavSettings.CHUNK_LOAD_RADIUS, NetworkSettings.RADAR_UPDATE_INTERVAL,
                NetworkSettings.ENTITY_TRACKING_RANGE_MULTIPLIER, RenderSettings.VEHICLE_RENDERER);
    }
}
