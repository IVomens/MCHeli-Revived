package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.hud.HudFrame;
import com.ivomens.mcheli.core.hud.HudTextValue;
import com.ivomens.mcheli.core.math.Vec2d;

import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fixed inputs for one HUD frame: the formula variables, the {@code DrawString} values and the instruments.
 * Each value has the Java type the game gives it, because {@code DrawString} formats with
 * {@link String#format} and {@code %d} rejects a double.
 */
record HudTraceFrame(String name, boolean autoScaleRequested, Map<String, Object> variables,
                     Map<HudTextValue, Object> strings, boolean hasWeapon, int magazineSize,
                     float[] cameraRotation, List<Vec2d> entityContacts, List<Vec2d> enemyContacts) {

    /** Level flight with a gun selected, drawn at the window's size. */
    static HudTraceFrame cruise() {
        return frame("cruise", false, vars -> {
            vars.put("altitude", 250);
            vars.put("hp", 120);
            vars.put("max_hp", 120);
            vars.put("throttle", 0.8);
            vars.put("yaw", 90.0F);
            vars.put("pitch", -5.25F);
            vars.put("roll", 15.5F);
            vars.put("motion_z", 0.3);
            vars.put("sight_type", 1);
            vars.put("have_flare", true);
            vars.put("can_flare", true);
            vars.put("inventory", 9);
        }, 30, 120, "M230 30mm", "300", "900", true, 300);
    }

    /** Low, damaged, reloading a heat-seeker, looking through the gunner camera, auto-scaled. */
    static HudTraceFrame damagedGunner() {
        return frame("damaged_gunner", true, vars -> {
            vars.put("altitude", 3);
            vars.put("hp", 37);
            vars.put("max_hp", 120);
            vars.put("low_fuel", 1);
            vars.put("fuel", 0.05F);
            vars.put("reloading", true);
            vars.put("reload_time", 0.3F);
            vars.put("is_heat_wpn", true);
            vars.put("wpn_heat", 0.5);
            vars.put("sight_type", 2);
            vars.put("lock", 0.4);
            vars.put("cam_mode", 1);
            vars.put("cam_zoom", 2.5F);
            vars.put("gunner_mode", true);
            vars.put("roll", -30.0F);
            vars.put("pitch", 10.0F);
        }, 3, 37, "AIM-9 Sidewinder", "1", "4", true, 2);
    }

    /** A UAV with a weak signal and no weapon selected. */
    static HudTraceFrame uav() {
        return frame("uav_no_weapon", false, vars -> {
            vars.put("is_uav", true);
            vars.put("uav_fs", 0.005);
            vars.put("uav_dist", 350.5);
            vars.put("cam_mode", 2);
            vars.put("dsp_mt_dist", true);
            vars.put("altitude", 57);
            vars.put("hp", 400);
            vars.put("max_hp", 400);
            vars.put("vtol_stat", 2);
            vars.put("auto_pilot", 1);
            vars.put("free_look", 1);
        }, 57, 400, "", "", "", false, 0);
    }

    static List<HudTraceFrame> all() {
        return List.of(cruise(), damagedGunner(), uav());
    }

    private static HudTraceFrame frame(String name, boolean autoScale, Consumer<Map<String, Object>> overrides,
                                       int altitude, int hp, String weapon, String ammo, String reserve,
                                       boolean hasWeapon, int magazine) {
        Map<String, Object> vars = new LinkedHashMap<>(HudVariables.frames(1).getFirst());
        overrides.accept(vars);
        HudVariables.derive(vars);

        Map<HudTextValue, Object> strings = new EnumMap<>(HudTextValue.class);
        strings.put(HudTextValue.NONE, null);
        strings.put(HudTextValue.NAME, "AH-64D Apache");
        strings.put(HudTextValue.ALTITUDE, altitude);
        strings.put(HudTextValue.DATE, new Date(0L));
        strings.put(HudTextValue.MC_THOR, 14);
        strings.put(HudTextValue.MC_TMIN, 5);
        strings.put(HudTextValue.MC_TSEC, 9);
        strings.put(HudTextValue.MAX_HP, (Integer) vars.get("max_hp"));
        strings.put(HudTextValue.HP, hp);
        strings.put(HudTextValue.HP_PER, vars.get("hp_per"));
        strings.put(HudTextValue.POS_X, vars.get("pos_x"));
        strings.put(HudTextValue.POS_Y, vars.get("pos_y"));
        strings.put(HudTextValue.POS_Z, vars.get("pos_z"));
        strings.put(HudTextValue.MOTION_X, vars.get("motion_x"));
        strings.put(HudTextValue.MOTION_Y, vars.get("motion_y"));
        strings.put(HudTextValue.MOTION_Z, vars.get("motion_z"));
        strings.put(HudTextValue.INVENTORY, vars.get("inventory"));
        strings.put(HudTextValue.WPN_NAME, weapon);
        strings.put(HudTextValue.WPN_AMMO, ammo);
        strings.put(HudTextValue.WPN_RM_AMMO, reserve);
        strings.put(HudTextValue.RELOAD_PER, 30.0F);
        strings.put(HudTextValue.RELOAD_SEC, 1.5F);
        strings.put(HudTextValue.MORTAR_DIST, 120.5F);
        strings.put(HudTextValue.MC_VER, "1.21.1");
        strings.put(HudTextValue.MOD_VER, "test");
        strings.put(HudTextValue.MOD_NAME, "MC Helicopter MOD");
        strings.put(HudTextValue.YAW, 270.0);
        strings.put(HudTextValue.PITCH, 5.25F);
        strings.put(HudTextValue.ROLL, (Float) vars.get("roll"));
        strings.put(HudTextValue.PLYR_YAW, 225.25);
        strings.put(HudTextValue.PLYR_PITCH, 12.5F);
        strings.put(HudTextValue.TVM_POS_X, 0.0);
        strings.put(HudTextValue.TVM_POS_Y, 0.0);
        strings.put(HudTextValue.TVM_POS_Z, 0.0);
        strings.put(HudTextValue.TVM_DIFF, vars.get("tvm_diff"));
        strings.put(HudTextValue.CAM_ZOOM, vars.get("cam_zoom"));
        strings.put(HudTextValue.UAV_DIST, vars.get("uav_dist"));
        strings.put(HudTextValue.KEY_GUI, "R");
        strings.put(HudTextValue.THROTTLE, (Double) vars.get("throttle") * 100.0);
        strings.put(HudTextValue.AIRBURST_DIST, "400m");

        List<Vec2d> entities = List.of(new Vec2d(10.0, -20.0), new Vec2d(-50.0, 30.0), new Vec2d(70.0, 70.0));
        List<Vec2d> enemies = List.of(new Vec2d(10.0, -20.0));
        return new HudTraceFrame(name, autoScale, vars, strings, hasWeapon, magazine,
                new float[]{90.0F, 120.0F, 35.0F}, entities, enemies);
    }

    /** The frame the game would have read from the vehicle. */
    HudFrame toFrame() {
        HudFrame frame = HudFrames.of(this.variables);
        this.strings.forEach(frame::setText);
        frame.setWeapon(this.hasWeapon, this.magazineSize);
        frame.setCamera(this.cameraRotation[0], this.cameraRotation[1], this.cameraRotation[2]);
        frame.setRadar(this.entityContacts, this.enemyContacts);
        return frame;
    }
}
