package com.ivomens.mcheli.hud.client;

import com.ivomens.mcheli.aircraft.AircraftCamera;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.config.client.KeyNames;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.content.def.weapon.SightType;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.client.overlay.HudCanvas;
import com.ivomens.mcheli.core.hud.HudFrame;
import com.ivomens.mcheli.core.hud.HudTextValue;
import com.ivomens.mcheli.core.hud.HudVariable;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.platform.Services;
import com.ivomens.mcheli.util.LowPassFilterFloat;
import com.ivomens.mcheli.core.math.Degrees;
import com.ivomens.mcheli.core.math.Vec2d;
import com.ivomens.mcheli.vehicle.client.input.MouseStick;
import com.ivomens.mcheli.vehicle.sensor.IncomingMissiles;
import com.ivomens.mcheli.vehicle.sensor.client.IncomingMissileReader;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;

/**
 * Reads a vehicle into a {@link HudFrame}, once for every frame a scripted HUD draws: the formula variables,
 * the {@code DrawString} values and the instrument inputs, from the vehicle's components and the viewer.
 * <p>
 * Some readings carry over between frames, which is why this is an object: ground altitude is sampled every
 * {@value #ALTITUDE_SAMPLE_FRAMES} frames, the stick is smoothed, the radar sweep is unwrapped so it does not
 * spin backwards, and the low fuel warning blinks.
 */
public final class HudTelemetry {

    /** Ground altitude needs a column scan, so it is refreshed every this many frames. */
    private static final int ALTITUDE_SAMPLE_FRAMES = 30;
    /** The low fuel warning blinks: on for the last {@link #FUEL_WARN_ON_FRAMES} of every period. */
    private static final int FUEL_WARN_PERIOD_FRAMES = 280;
    private static final int FUEL_WARN_ON_FRAMES = 160;
    private static final float LOW_FUEL_FRACTION = 0.1F;
    private static final int STICK_FILTER_LENGTH = 4;
    private static final int AIRBURST_MIN = 5;
    private static final int AIRBURST_MAX = 3000;
    private static final int TICKS_PER_DAY = 24000;
    private static final int SIGHT_NONE = 0;
    private static final int SIGHT_ROCKET = 1;
    private static final int SIGHT_LOCK = 2;
    private static final String RELOADING_NAME = "-- Reloading --";
    private static final String RELOADING_AMMO = "----";
    private static final String MC_VERSION = "1.21.1";
    private static final String MOD_NAME = "MC Helicopter MOD";

    private final MouseStick stick;
    private final LowPassFilterFloat stickXFilter = new LowPassFilterFloat(STICK_FILTER_LENGTH);
    private final LowPassFilterFloat stickYFilter = new LowPassFilterFloat(STICK_FILTER_LENGTH);
    private int framesUntilAltitudeSample;
    private int fuelWarnCounter;
    private float previousRadarRotation;
    private int altitude;
    /** The weapon status texts keep their last value while no weapon is selected. */
    private String weaponName = "";
    private String weaponAmmo = "";
    private String weaponReserveAmmo = "";
    private float reloadPercent;
    private float reloadSeconds;
    private float mortarDistance;
    private String airburstDistance = "";

    /** @param stick the mouse stick, whose deflection becomes {@code STICK_X} and {@code STICK_Y} */
    public HudTelemetry(MouseStick stick) {
        this.stick = stick;
    }

    /** Fills {@code frame} for one frame of {@code vehicle} as {@code viewer} sees it on {@code canvas}. */
    public void read(HudFrame frame, AircraftEntity vehicle, Player viewer, float partialTicks, HudCanvas canvas) {
        updateAltitude(vehicle);
        WeaponGroup weapon = vehicle.weapons().current(viewer);
        updateWeaponStatus(vehicle, viewer, weapon);

        readScreen(frame, canvas);
        readAttitude(frame, vehicle, viewer);
        readCondition(frame, vehicle, viewer, partialTicks);
        readWeapon(frame, weapon);
        readModes(frame, vehicle, viewer);
        readTexts(frame, vehicle, viewer, weapon);
        readInstruments(frame, vehicle, weapon);
    }

    private static void readScreen(HudFrame frame, HudCanvas canvas) {
        double width = canvas.width();
        double height = canvas.height();
        frame.set(HudVariable.CENTER_X, width / 2.0);
        frame.set(HudVariable.CENTER_Y, height / 2.0);
        frame.set(HudVariable.WIDTH, width);
        frame.set(HudVariable.HEIGHT, height);
    }

    private static void readAttitude(HudFrame frame, AircraftEntity vehicle, Player viewer) {
        float[] barrel = vehicle.weapons().shot().direction(viewer);
        frame.set(HudVariable.TIME, viewer.level().getDayTime() % TICKS_PER_DAY);
        frame.set(HudVariable.TEST_MODE, McheliConfig.debug().testMode());
        frame.set(HudVariable.PLYR_YAW, Mth.wrapDegrees(viewer.getYRot()));
        frame.set(HudVariable.PLYR_PITCH, viewer.getXRot());
        frame.set(HudVariable.BRL_YAW, barrel[0]);
        frame.set(HudVariable.BRL_PITCH, barrel[1]);
        frame.set(HudVariable.YAW, Mth.wrapDegrees(vehicle.getYRot()));
        frame.set(HudVariable.PITCH, vehicle.getXRot());
        frame.set(HudVariable.ROLL, Mth.wrapDegrees(vehicle.roll()));
    }

    private void readCondition(HudFrame frame, AircraftEntity vehicle, Player viewer, float partialTicks) {
        int hp = vehicle.damage().hp();
        int maxHp = vehicle.damage().maxHp();
        Vec3 motion = vehicle.getDeltaMovement();
        frame.set(HudVariable.ALTITUDE, this.altitude);
        frame.set(HudVariable.SEA_ALT, Math.max(vehicle.getY() - vehicle.level().getSeaLevel(), 0.0));
        frame.set(HudVariable.HAVE_RADAR, vehicle.radar().hasScope());
        frame.set(HudVariable.RADAR_ROT, radarRotation(vehicle, partialTicks));
        frame.set(HudVariable.HP, hp);
        frame.set(HudVariable.MAX_HP, maxHp);
        frame.set(HudVariable.HP_RTO, maxHp > 0 ? (double) hp / maxHp : 0.0);
        frame.set(HudVariable.HP_PER, maxHp > 0 ? 100.0 * hp / maxHp : 0.0);
        frame.set(HudVariable.THROTTLE, vehicle.engine().throttle());
        frame.set(HudVariable.POS_X, vehicle.getX());
        frame.set(HudVariable.POS_Y, vehicle.getY());
        frame.set(HudVariable.POS_Z, vehicle.getZ());
        frame.set(HudVariable.MOTION_X, motion.x);
        frame.set(HudVariable.MOTION_Y, motion.y);
        frame.set(HudVariable.MOTION_Z, motion.z);
        frame.set(HudVariable.SPEED, motion.length());
        frame.set(HudVariable.FUEL, vehicle.fuel().percentage());
        frame.set(HudVariable.LOW_FUEL, lowFuelBlink(vehicle, viewer) ? 1 : 0);
        double maxStick = MouseStick.MAX_LENGTH;
        this.stickXFilter.put((float) (this.stick.stickX() / maxStick));
        this.stickYFilter.put((float) (-this.stick.stickY() / maxStick));
        frame.set(HudVariable.STICK_X, this.stickXFilter.getAvg());
        frame.set(HudVariable.STICK_Y, this.stickYFilter.getAvg());
    }

    private void readWeapon(HudFrame frame, @Nullable WeaponGroup weapon) {
        WeaponMount mount = weapon != null ? weapon.currentMount() : null;
        WeaponSpec info = mount != null ? mount.spec() : null;
        boolean heatWeapon = info != null && info.missile().heat().maxHeatCount() > 0;
        int lockTicks = mount != null && mount.seeker() != null ? mount.seeker().lockTicks() : 0;
        boolean lockSight = info != null && info.camera().sight() == SightType.LOCK && lockTicks > 0;
        int sight = lockSight ? SIGHT_LOCK : SIGHT_NONE;
        if (info != null && info.camera().sight() == SightType.ROCKET) {
            sight = SIGHT_ROCKET;
        }
        frame.set(HudVariable.RELOADING, info != null && weapon.isBusy());
        frame.set(HudVariable.RELOAD_TIME, info != null ? reloadFraction(weapon, info) : 0.0F);
        frame.set(HudVariable.WPN_HEAT, heatWeapon
                ? Math.min((double) weapon.heat() / info.missile().heat().maxHeatCount(), 1.0) : 0.0);
        frame.set(HudVariable.IS_HEAT_WPN, heatWeapon);
        frame.set(HudVariable.SIGHT_TYPE, sight);
        frame.set(HudVariable.LOCK, lockSight ? (double) mount.seeker().lockCount() / lockTicks : 0.0);
        frame.set(HudVariable.DSP_MT_DIST, info != null && info.camera().displayMortarDistance());
        frame.set(HudVariable.MT_DIST, this.mortarDistance);
        frame.set(HudVariable.HAS_AIRBURST, info != null && mount.airburstDistance() != 0 ? 1 : 0);
    }

    /** How far the weapon is from firing again, by whichever of its delay and reload is the longer. */
    private static float reloadFraction(WeaponGroup weapon, WeaponSpec info) {
        if (info.delay() > info.ammo().reloadTime()) {
            return Math.min(Math.abs((float) weapon.cooldown() / Math.max(info.delay(), 1)), 1.0F);
        }
        return (float) weapon.reloadTicks() / Math.max(info.ammo().reloadTime(), 1);
    }

    private static void readModes(HudFrame frame, AircraftEntity vehicle, Player viewer) {
        boolean pilot = vehicle.isPilot(viewer);
        boolean gunnerView = vehicle.modes().usesGunnerView(viewer);
        frame.set(HudVariable.VTOL_STAT, vehicle instanceof PlaneEntity plane ? plane.nozzles().mode() : 0);
        frame.set(HudVariable.FREE_LOOK, pilot && vehicle.modes().canSwitchFreeLook() && vehicle.modes().isFreeLook() ? 1 : 0);
        frame.set(HudVariable.GUNNER_MODE, gunnerView);
        frame.set(HudVariable.CAM_MODE, vehicle.cameras().mode(viewer));
        frame.set(HudVariable.CAM_ZOOM, vehicle.cameras().camera().getCameraZoom());
        frame.set(HudVariable.AUTO_PILOT, vehicle instanceof PlaneEntity && pilot && gunnerView ? 1 : 0);
        frame.set(HudVariable.HAVE_FLARE, vehicle.countermeasures().haveFlare());
        frame.set(HudVariable.CAN_FLARE, vehicle.countermeasures().canUseFlare());
        frame.set(HudVariable.INVENTORY, vehicle.getSizeInventory());
        frame.set(HudVariable.HOVERING, vehicle instanceof HelicopterEntity && vehicle.modes().isHoveringMode());
        frame.set(HudVariable.IS_UAV, vehicle.isUAV());
        frame.set(HudVariable.UAV_FS, vehicle.uavLink().signalStrength());
        frame.set(HudVariable.UAV_DIST, uavDistance(vehicle));
        frame.set(HudVariable.TVM_DIFF, tvMissileDistance(vehicle));
        IncomingMissiles incoming = IncomingMissileReader.read(vehicle);
        frame.set(HudVariable.MSL_HEAT, incoming.heat());
        frame.set(HudVariable.MSL_RADAR, incoming.radar());
        frame.set(HudVariable.MSL_COUNT, incoming.total());
    }

    private void readTexts(HudFrame frame, AircraftEntity vehicle, Player viewer, @Nullable WeaponGroup weapon) {
        int maxHp = vehicle.damage().maxHp();
        int timeOfDay = (int) ((vehicle.level().getDayTime() + 6000L) % 24000L);
        Entity tvMissile = vehicle.cameras().tvMissile();
        Vec3 motion = vehicle.getDeltaMovement();
        frame.setText(HudTextValue.NAME, vehicle.spec().file().displayName().name());
        frame.setText(HudTextValue.ALTITUDE, this.altitude);
        frame.setText(HudTextValue.DATE, new Date());
        frame.setText(HudTextValue.MC_THOR, timeOfDay / 1000);
        frame.setText(HudTextValue.MC_TMIN, timeOfDay % 1000 * 36 / 10 / 60);
        frame.setText(HudTextValue.MC_TSEC, timeOfDay % 1000 * 36 / 10 % 60);
        frame.setText(HudTextValue.MAX_HP, maxHp);
        frame.setText(HudTextValue.HP, vehicle.damage().hp());
        frame.setText(HudTextValue.HP_PER, (maxHp > 0 ? (double) vehicle.damage().hp() / maxHp : 0.0) * 100.0);
        frame.setText(HudTextValue.POS_X, vehicle.getX());
        frame.setText(HudTextValue.POS_Y, vehicle.getY());
        frame.setText(HudTextValue.POS_Z, vehicle.getZ());
        frame.setText(HudTextValue.MOTION_X, motion.x);
        frame.setText(HudTextValue.MOTION_Y, motion.y);
        frame.setText(HudTextValue.MOTION_Z, motion.z);
        frame.setText(HudTextValue.INVENTORY, vehicle.getSizeInventory());
        frame.setText(HudTextValue.WPN_NAME, this.weaponName);
        frame.setText(HudTextValue.WPN_AMMO, this.weaponAmmo);
        frame.setText(HudTextValue.WPN_RM_AMMO, this.weaponReserveAmmo);
        frame.setText(HudTextValue.RELOAD_PER, this.reloadPercent);
        frame.setText(HudTextValue.RELOAD_SEC, this.reloadSeconds);
        frame.setText(HudTextValue.MORTAR_DIST, this.mortarDistance);
        frame.setText(HudTextValue.MC_VER, MC_VERSION);
        frame.setText(HudTextValue.MOD_VER, Services.PLATFORM.getModVersion());
        frame.setText(HudTextValue.MOD_NAME, MOD_NAME);
        frame.setText(HudTextValue.YAW, Degrees.wrap360(vehicle.getYRot() + 180.0F));
        frame.setText(HudTextValue.PITCH, -vehicle.getXRot());
        frame.setText(HudTextValue.ROLL, Mth.wrapDegrees(vehicle.roll()));
        frame.setText(HudTextValue.PLYR_YAW, Degrees.wrap360(viewer.getYRot() + 180.0F));
        frame.setText(HudTextValue.PLYR_PITCH, -viewer.getXRot());
        frame.setText(HudTextValue.TVM_POS_X, tvMissile != null ? tvMissile.getX() : 0.0);
        frame.setText(HudTextValue.TVM_POS_Y, tvMissile != null ? tvMissile.getY() : 0.0);
        frame.setText(HudTextValue.TVM_POS_Z, tvMissile != null ? tvMissile.getZ() : 0.0);
        frame.setText(HudTextValue.TVM_DIFF, tvMissileDistance(vehicle));
        frame.setText(HudTextValue.CAM_ZOOM, vehicle.cameras().camera().getCameraZoom());
        frame.setText(HudTextValue.UAV_DIST, uavDistance(vehicle));
        frame.setText(HudTextValue.KEY_GUI, KeyNames.getDescOrName(McheliConfig.keys().gui()));
        frame.setText(HudTextValue.THROTTLE, vehicle.engine().throttle() * 100.0);
        frame.setText(HudTextValue.AIRBURST_DIST, this.airburstDistance);
    }

    private static void readInstruments(HudFrame frame, AircraftEntity vehicle, @Nullable WeaponGroup weapon) {
        frame.setWeapon(weapon != null, weapon != null ? weapon.magazineSize() : 0);
        AircraftCamera camera = vehicle.cameras().camera();
        if (camera != null) {
            frame.setCamera(vehicle.getYRot(), camera.rotationYaw(), camera.rotationPitch());
        } else {
            frame.clearCamera();
        }
        frame.setRadar(orEmpty(vehicle.radar().contacts()), orEmpty(vehicle.radar().enemies()));
    }

    private static List<Vec2d> orEmpty(@Nullable List<Vec2d> contacts) {
        return contacts != null ? contacts : List.of();
    }

    /** Height above the first non-air block below the vehicle; keeps the last value over the void. */
    private void updateAltitude(AircraftEntity vehicle) {
        if (this.framesUntilAltitudeSample > 0) {
            this.framesUntilAltitudeSample--;
            return;
        }
        this.framesUntilAltitudeSample = ALTITUDE_SAMPLE_FRAMES;

        Level level = vehicle.level();
        int top = level.getMaxBuildHeight() - 1;
        int startY = Math.min(Mth.floor(vehicle.getY()), top);
        int bottom = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(vehicle.getX()), 0, Mth.floor(vehicle.getZ()));
        for (int depth = 0; startY - depth > bottom; depth++) {
            pos.setY(startY - depth);
            if (!level.getBlockState(pos).isAir()) {
                this.altitude = depth;
                if (vehicle.getY() > top) {
                    this.altitude += (int) (vehicle.getY() - top);
                }
                return;
            }
        }
    }

    private void updateWeaponStatus(AircraftEntity vehicle, Player viewer, @Nullable WeaponGroup weapon) {
        if (weapon == null || vehicle.weapons().count() <= 0) return;

        boolean reloading = vehicle.ammoSupply().isReloading();
        this.weaponName = reloading ? RELOADING_NAME : weapon.displayName();
        if (weapon.magazineSize() > 0) {
            this.weaponAmmo = reloading ? RELOADING_AMMO : String.format("%4d", weapon.ammo());
            this.weaponReserveAmmo = reloading ? RELOADING_AMMO : String.format("%4d", weapon.reserveAmmo());
        } else {
            this.weaponAmmo = "";
            this.weaponReserveAmmo = "";
        }

        WeaponSpec info = weapon.spec();
        if (info == null) return;
        this.mortarDistance = info.camera().displayMortarDistance()
                ? (float) vehicle.weapons().impact().getLandInDistance(viewer) : -1.0F;
        float cooldownTicks = info.delay() > info.ammo().reloadTime() ? Math.abs(weapon.cooldown())
                : weapon.reloadTicks();
        this.reloadSeconds = cooldownTicks / 20.0F;
        this.reloadPercent = (1.0F - reloadFraction(weapon, info)) * 100.0F;

        WeaponMount mount = weapon.currentMount();
        if (mount == null || mount.airburstDistance() <= AIRBURST_MIN || mount.airburstDistance() >= AIRBURST_MAX) {
            this.airburstDistance = "---";
        } else {
            this.airburstDistance = mount.airburstDistance() + "m + 3";
        }
    }

    /** Unwraps the radar sweep across 360 degrees so interpolation does not spin backwards. */
    private float radarRotation(AircraftEntity vehicle, float partialTicks) {
        float rotation = vehicle.radar().sweepAngle();
        float previous = this.previousRadarRotation;
        this.previousRadarRotation = rotation;
        if (rotation < previous) {
            rotation += 360.0F;
        }
        return Mth.lerp(partialTicks, previous, rotation);
    }

    /** True while the blinking low fuel warning is in its visible phase. */
    private boolean lowFuelBlink(AircraftEntity vehicle, Player viewer) {
        if (this.fuelWarnCounter <= 0) {
            this.fuelWarnCounter = FUEL_WARN_PERIOD_FRAMES;
        }
        this.fuelWarnCounter--;
        return this.fuelWarnCounter < FUEL_WARN_ON_FRAMES && vehicle.fuel().capacity() > 0
                && vehicle.fuel().percentage() < LOW_FUEL_FRACTION && !vehicle.fuel().isInfinite(viewer, false);
    }

    private static double tvMissileDistance(AircraftEntity vehicle) {
        Entity missile = vehicle.cameras().tvMissile();
        return missile != null ? Math.sqrt(missile.distanceToSqr(vehicle)) : 0.0;
    }

    /** Horizontal distance to the UAV station, rounded to a float as it always was. */
    private static double uavDistance(AircraftEntity vehicle) {
        if (!vehicle.isUAV() || vehicle.uavLink().station() == null) {
            return 0.0;
        }
        Vec3 station = vehicle.uavLink().station().getPos();
        double dx = vehicle.getX() - station.x;
        double dz = vehicle.getZ() - station.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }
}
