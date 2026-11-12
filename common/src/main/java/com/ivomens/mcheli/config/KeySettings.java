package com.ivomens.mcheli.config;

import com.ivomens.mcheli.config.client.KeyNames;
import java.util.List;

/**
 * Client key bindings, as LWJGL 2 key codes (negative numbers are mouse buttons, see {@code KeyNames}).
 * The defaults are noted next to each option.
 */
public record KeySettings(int up, int down, int right, int left, int switchMode, int switchHovering,
                          int switchWeapon1, int switchWeapon2, int switchWeaponMode, int reloadWeapon, int zoom,
                          int cameraMode, int unmount, int flare, int chaff, int aps, int ecmJammer,
                          int airburstDistReset, int radarSwitch, int openGpsPanel, int extra, int cameraDistUp,
                          int cameraDistDown, int freeLook, int gui, int gearUpDown, int putToRack,
                          int downFromRack, int scoreboard, int multiplayManager) {

    /** Left mouse button. Attacking and firing are not configurable. */
    public static final int ATTACK = -100;
    public static final int USE_WEAPON = -100;

    public static final IntOption UP = key("KeyUp", 17); // W
    public static final IntOption DOWN = key("KeyDown", 31); // S
    public static final IntOption RIGHT = key("KeyRight", 32); // D
    public static final IntOption LEFT = key("KeyLeft", 30); // A
    public static final IntOption SWITCH_MODE = key("KeySwitchGunner", 35); // H
    public static final IntOption SWITCH_HOVERING = key("KeySwitchHovering", 57); // SPACE
    public static final IntOption SWITCH_WEAPON_1 = key("KeySwitchWeapon1", -98); // middle mouse button
    public static final IntOption SWITCH_WEAPON_2 = key("KeySwitchWeapon2", 34); // G
    public static final IntOption SWITCH_WEAPON_MODE = key("KeySwitchWeaponMode", 45); // X
    public static final IntOption RELOAD_WEAPON = key("KeyReloadWeapon", 19); // R
    public static final IntOption ZOOM = key("KeyZoom", 44); // Z
    public static final IntOption CAMERA_MODE = key("KeyCameraMode", 46); // C
    public static final IntOption UNMOUNT = key("KeyUnmountMob", 21); // Y
    public static final IntOption FLARE = key("KeyFlare", 47); // V
    public static final IntOption CHAFF = key("KeyChaff", 47); // V
    public static final IntOption APS = key("KeyAPS", 47); // V
    public static final IntOption ECM_JAMMER = key("KeyECMJammer", 47); // V
    public static final IntOption AIRBURST_DIST_RESET = key("KeyAirburstDistReset", -99); // right mouse button
    public static final IntOption RADAR_SWITCH = key("KeyRadarSwitch", 41); // GRAVE
    /** Opens the GPS panel for typing coordinates by hand. */
    public static final IntOption OPEN_GPS_PANEL = key("KeyOpenGPSPanel", 37); // K
    public static final IntOption EXTRA = key("KeyExtra", 33); // F
    public static final IntOption CAMERA_DIST_UP = key("KeyCameraDistanceUp", 201); // PRIOR
    public static final IntOption CAMERA_DIST_DOWN = key("KeyCameraDistanceDown", 209); // NEXT
    public static final IntOption FREE_LOOK = key("KeyFreeLook", 29); // LCONTROL
    public static final IntOption GUI = key("KeyGUI", 23); // I
    public static final IntOption GEAR_UP_DOWN = key("KeyGearUpDown", 48); // B
    public static final IntOption PUT_TO_RACK = key("KeyPutToRack", 36); // J
    public static final IntOption DOWN_FROM_RACK = key("KeyDownFromRack", 22); // U
    public static final IntOption SCOREBOARD = key("KeyScoreboard", 38); // L
    public static final IntOption MULTIPLAY_MANAGER = key("KeyMultiplayManager", 50); // M

    /** Every binding, in the order the file lists them. */
    public static final List<IntOption> ALL = List.of(UP, DOWN, RIGHT, LEFT, SWITCH_MODE, SWITCH_HOVERING,
            SWITCH_WEAPON_1, SWITCH_WEAPON_2, SWITCH_WEAPON_MODE, RELOAD_WEAPON, ZOOM, CAMERA_MODE, UNMOUNT, FLARE,
            CHAFF, APS, ECM_JAMMER, AIRBURST_DIST_RESET, RADAR_SWITCH, OPEN_GPS_PANEL, EXTRA, CAMERA_DIST_UP,
            CAMERA_DIST_DOWN, FREE_LOOK, GUI, GEAR_UP_DOWN, PUT_TO_RACK, DOWN_FROM_RACK, SCOREBOARD,
            MULTIPLAY_MANAGER);

    static KeySettings from(ConfigValues v) {
        return new KeySettings(v.get(UP), v.get(DOWN), v.get(RIGHT), v.get(LEFT), v.get(SWITCH_MODE),
                v.get(SWITCH_HOVERING), v.get(SWITCH_WEAPON_1), v.get(SWITCH_WEAPON_2), v.get(SWITCH_WEAPON_MODE),
                v.get(RELOAD_WEAPON), v.get(ZOOM), v.get(CAMERA_MODE), v.get(UNMOUNT), v.get(FLARE), v.get(CHAFF),
                v.get(APS), v.get(ECM_JAMMER), v.get(AIRBURST_DIST_RESET), v.get(RADAR_SWITCH),
                v.get(OPEN_GPS_PANEL), v.get(EXTRA), v.get(CAMERA_DIST_UP), v.get(CAMERA_DIST_DOWN),
                v.get(FREE_LOOK), v.get(GUI), v.get(GEAR_UP_DOWN), v.get(PUT_TO_RACK), v.get(DOWN_FROM_RACK),
                v.get(SCOREBOARD), v.get(MULTIPLAY_MANAGER));
    }

    private static IntOption key(String name, int defaultCode) {
        return IntOption.of(name, defaultCode);
    }
}
