package com.ivomens.mcheli.aircraft.client;

import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.client.render.ScreenProjection;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponKind;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.config.client.KeyNames;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.client.overlay.HudCanvas.LineMode;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import com.ivomens.mcheli.util.Terrain;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.vehicle.GroundVehicleEntity;
import com.ivomens.mcheli.vehicle.component.WeaponComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared HUD drawing for all vehicle types: hit markers, ballistics, debug info and key hints. */
public abstract class AircraftHudBase extends HudOverlay {

    private static final int DEBUG_TEXT_COLOR = 0xAFFFFFFF;
    private static final int PILOT_SEAT_KEY_COLOR = 0x00FF30;
    private static final int REPELLING_READY_COLOR = 0x00FF00;
    /** Simulation steps of the trajectory preview (one per tick). */
    private static final int MAX_TRAJECTORY_STEPS = 256;

    protected int currentLeftY;
    protected int currentRightY;
    private final double[] projBuf = new double[3];
    private final ScreenProjection projection;

    public AircraftHudBase(Minecraft minecraft, ScreenProjection projection) {
        super(minecraft);
        this.projection = projection;
    }

    protected boolean isExperimentalAutoScaleAircraftGui() {
        return McheliConfig.hud().autoScaleAircraftGui();
    }

    /** Position, view and barrel angles, shown with debug logging enabled. */
    public void drawDebugInfo(AircraftEntity ac) {
        Player player = this.mc.player;
        if (!McheliConfig.debug().debugLog() || player == null) {
            return;
        }
        int x = this.centerX - 145;
        int y = this.centerY;
        this.drawString(String.format("X: %+.1f", ac.getX()), x, y, DEBUG_TEXT_COLOR);
        this.drawString(String.format("Y: %+.1f", ac.getY()), x, y + 10, DEBUG_TEXT_COLOR);
        this.drawString(String.format("Z: %+.1f", ac.getZ()), x, y + 20, DEBUG_TEXT_COLOR);
        this.drawString(String.format("AX: %+.1f", player.getYRot()), x, y + 40, DEBUG_TEXT_COLOR);
        this.drawString(String.format("AY: %+.1f", player.getXRot()), x, y + 50, DEBUG_TEXT_COLOR);

        WeaponGroup weaponSet = ac.weapons().current(player);
        if (weaponSet != null && weaponSet.currentMount() != null) {
            float[] barrelDir = ac.weapons().shot().direction(player);
            this.drawString(String.format("BX: %+.1f", barrelDir[0]), x, y + 70, DEBUG_TEXT_COLOR);
            this.drawString(String.format("BY: %+.1f", barrelDir[1]), x, y + 80, DEBUG_TEXT_COLOR);
        }
    }

    public void drawNightVisionNoise() {
        this.drawScreenNoise("textures/gui/alpha.png", 0.0F, 1.0F, 0.0F, 0.3F);
    }

    /**
     * Four short diagonal strokes around the crosshair that fade as the hit gets older. The color
     * comes from the hit marker config; {@code baseColor} is not used.
     */
    public void drawHitMarker(int hitStrength, int maxHitStrength, int baseColor) {
        if (hitStrength <= 0) {
            return;
        }
        final int outer = 10;
        final int inner = 5;
        int cx = this.centerX;
        int cy = this.centerY;
        double[] lines = {
                cx - outer, cy - outer, cx - inner, cy - inner,
                cx - outer, cy + outer, cx - inner, cy + inner,
                cx + outer, cy - outer, cx + inner, cy - inner,
                cx + outer, cy + outer, cx + inner, cy + inner,
        };
        int alpha = hitStrength * (256 / maxHitStrength);
        this.drawLine(lines, (int) (McheliConfig.render().hitMarkAlpha() * alpha) << 24 | McheliConfig.render().hitMarkRgb());
    }

    public void drawHitMarker(AircraftEntity ac, int color, int seatID) {
        this.drawHitMarker(ac.weapons().hitMarkerTicks(), WeaponComponent.HIT_MARKER_TICKS, color);
    }

    protected void drawTvMissileNoise(AircraftEntity ac, ProjectileEntity tvmissile) {
        this.drawScreenNoise("textures/gui/noise.png", 0.5F, 0.5F, 0.5F, 0.4F);
    }

    private void drawSpreadCircle(double x, double y, double radius, int argb) {
        double[] pts = new double[CIRCLE_SEGMENTS * 2];
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            pts[i * 2] = x + CIRCLE_COS[i] * radius;
            pts[i * 2 + 1] = y + CIRCLE_SIN[i] * radius;
        }
        this.drawLine(pts, argb, LineMode.LINE_LOOP);
    }

    protected void drawTurretBallistics(AircraftEntity aircraft, Player player) {
        this.pushMatrix();
        this.invertBlend();
        double centerX = this.width / 2.0;
        double centerY = this.height / 2.0;
        this.drawLine(new double[]{
                centerX - 2, centerY, centerX + 2, centerY,
                centerX, centerY - 2, centerX, centerY + 2
        }, 0xFF00FF00);
        this.drawDetachedTurretDot(aircraft, player);
        this.disableBlend();
        this.popMatrix();
    }

    protected void drawDetachedTurretDot(AircraftEntity aircraft, Player player) {
        WeaponGroup weaponSet = aircraft.weapons().current(player);
        WeaponMount weapon = weaponSet != null ? weaponSet.currentMount() : null;
        if (weapon == null || weapon.spec() == null) {
            return;
        }

        float[] shotDir = aircraft.weapons().shot().direction(player, this.smoothCamPartialTicks);
        Vec3 look = Rotations.rotate(0.0, 0.0, 1.0, -shotDir[0], -shotDir[1], 0.0F).normalize();
        Vec3 target = getWeaponTrajectoryTarget(aircraft, player, weapon, look, this.smoothCamPartialTicks);

        if (!this.projection.isValid()) {
            return;
        }
        Vec3 cameraPos = this.projection.cameraPosition();
        Vec3 camLookVec = new Vec3(this.mc.gameRenderer.getMainCamera().getLookVector());
        Vec3 dirToTarget = target.subtract(cameraPos).normalize();
        boolean isInFront = camLookVec.dot(dirToTarget) > 0;

        if (isInFront && this.projection.project(target.x, target.y, target.z, this.projBuf)) {
            double screenX = this.projBuf[0];
            double screenY = this.projBuf[1];
            double distanceToCamera = cameraPos.distanceTo(target);
            double physicalRadius = distanceToCamera * Math.tan(Math.toRadians(weapon.spec().definition().accuracy() * 0.5F));
            double finalRadius = Math.max(physicalRadius * this.projection.pixelsPerUnit(this.projBuf[2]), 2.0);

            drawSpreadCircle(screenX, screenY, finalRadius, 0xCCffffff);

            if (finalRadius > 2.0) {
                this.drawLine(new double[]{
                        screenX - 2, screenY, screenX + 2, screenY,
                        screenX, screenY - 2, screenX, screenY + 2
                }, 0xCCFFFFFF);
            }
        }
    }

    /**
     * Simulates the current weapon's shot tick by tick and returns where it hits a block. Shots that
     * never come down within the simulated time report their apex instead.
     */
    protected Vec3 getWeaponTrajectoryTarget(AircraftEntity aircraft, Player player, WeaponMount weapon, Vec3 look, float partialTicks) {
        WeaponSpec info = weapon.spec();
        Vec3 interpolated = new Vec3(aircraft.xOld + (aircraft.getX() - aircraft.xOld) * partialTicks,
                aircraft.yOld + (aircraft.getY() - aircraft.yOld) * partialTicks,
                aircraft.zOld + (aircraft.getZ() - aircraft.zOld) * partialTicks);
        Vec3 currentPos;
        if (aircraft instanceof GroundVehicleEntity vehicle && aircraft.aim().isDetachedActive()) {
            currentPos = vehicle.weapons().shot().muzzleOffset(weapon.position(), player, partialTicks).add(interpolated);
        } else {
            currentPos = aircraft.weapons().shooter(null).muzzleOffset(weapon).add(interpolated);
        }
        WeaponKind.Trajectory trajectory = weapon.kind().trajectory();
        Vec3 motion = previewLaunchMotion(aircraft, weapon, trajectory, look);
        boolean gun = trajectory == WeaponKind.Trajectory.GUN;
        // fast bullets and rockets cover their speed in several sub-steps; the preview scales the step
        boolean steps = gun || trajectory == WeaponKind.Trajectory.ROCKET;
        double stepScale = info.ballistics().acceleration() > 4.0F && steps ? info.ballistics().acceleration() / 4.0F
                : 1.0;
        if (gun) {
            currentPos = currentPos.add(motion.x * 0.5, motion.y * 0.5, motion.z * 0.5);
        }
        return previewFlight(aircraft, info, currentPos, motion, stepScale, look);
    }

    private static Vec3 previewLaunchMotion(AircraftEntity aircraft, WeaponMount weapon, WeaponKind.Trajectory trajectory,
                                            Vec3 look) {
        Vec3 motion = aircraft.getDeltaMovement();
        return switch (trajectory) {
            case BOMB -> motion;
            case TORPEDO -> look.scale(weapon.spec().ballistics().acceleration()).add(motion);
            default -> look.scale(Math.min(weapon.stats().speed(), 3.9F));
        };
    }

    /** Steps the preview until it hits a block; a shot that never comes down reports its apex. */
    private static Vec3 previewFlight(AircraftEntity aircraft, WeaponSpec info, Vec3 start, Vec3 launch,
            double stepScale,
                                      Vec3 look) {
        Vec3 currentPos = start;
        Vec3 apexPos = start;
        double highestY = start.y;
        double mx = launch.x;
        double my = launch.y;
        double mz = launch.z;
        int maxSteps = info.explosion().fuseTime() > 0 ? Math.min(MAX_TRAJECTORY_STEPS, info.explosion().fuseTime())
                : MAX_TRAJECTORY_STEPS;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int step = 0; step < maxSteps; step++) {
            blockPos.set(currentPos.x, currentPos.y, currentPos.z);
            if (!aircraft.level().hasChunkAt(blockPos)) {
                break;
            }
            boolean inWater = aircraft.level().getFluidState(blockPos).is(FluidTags.WATER);
            if (currentPos.y > highestY) {
                highestY = currentPos.y;
                apexPos = currentPos;
            }
            my += inWater ? info.ballistics().gravityInWater() : info.ballistics().gravity();
            if (inWater && info.ballistics().velocityInWater() > 0.0F) {
                mx *= info.ballistics().velocityInWater();
                my *= info.ballistics().velocityInWater();
                mz *= info.ballistics().velocityInWater();
            }
            Vec3 nextPos = new Vec3(currentPos.x + mx * stepScale, currentPos.y + my * stepScale, currentPos.z + mz * stepScale);
            BlockHitResult hit = aircraft.level().clip(new ClipContext(currentPos, nextPos, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, aircraft));
            if (hit.getType() == HitResult.Type.BLOCK) {
                return hit.getLocation();
            }
            currentPos = nextPos;
        }
        return my >= 0 || look.y > 0 ? apexPos : currentPos;
    }

    public void drawAircraftKeyBinds(AircraftEntity aircraft, VehicleSpec info, Player player, int seatID,
                                     LayoutTheme theme) {
        if (seatID == 0) {
            drawRackKeys(aircraft, theme);
        }
        drawSeatKeys(aircraft, seatID, theme);

        if (seatID >= 0 && seatID <= 1 && aircraft.countermeasures().haveFlare()) {
            var flareColor = aircraft.countermeasures().isFlarePreparation() ? theme.inactive() : theme.active();
            drawRightKey("Flare", McheliConfig.keys().flare(), theme.rightX(), flareColor);
        }

        if (seatID == 0 && info.parts().haveLandingGear()) {
            if (aircraft.parts().canFoldLandingGear()) {
                drawRightKey("Gear Up", McheliConfig.keys().gearUpDown(), theme.rightX(), theme.active());
            } else if (aircraft.parts().canUnfoldLandingGear()) {
                drawRightKey("Gear Down", McheliConfig.keys().gearUpDown(), theme.rightX(), theme.active());
            }
        }

        drawWeaponAndCameraKeys(aircraft, player, seatID, theme);

        if (seatID == 0 && aircraft.seats().count() >= 1) {
            var isParachuting = info.dropsCrew() && !Terrain.hasBlockY(aircraft, 3, -10);
            var isRepelling = aircraft.ropes().canStartRepelling();

            var dismountLabel = isParachuting ? "Parachuting" : (isRepelling ? "Repelling" : "Dismount");
            var dismountColor = isRepelling ? REPELLING_READY_COLOR : theme.active();

            drawLeftKey(dismountLabel, McheliConfig.keys().unmount(), theme.leftX(), dismountColor);
        }

        var canFreeLook = (seatID == 0 && aircraft.modes().canSwitchFreeLook()) ||
                (seatID > 0 && aircraft.modes().canSwitchGunnerModeOtherSeat(player));
        drawLeftConditional(canFreeLook, "FreeLook", McheliConfig.keys().freeLook(), theme.leftX(), theme.active());
    }

    /** The pilot's rack keys: loading, unloading or riding a rack, and leaving the rack it rides. */
    private void drawRackKeys(AircraftEntity aircraft, LayoutTheme theme) {
        drawLeftConditional(aircraft.racks().canLoadRack(), "PutRack", McheliConfig.keys().putToRack(), theme.leftX(), theme.active());
        drawLeftConditional(aircraft.racks().canUnloadRack(), "DownRack", McheliConfig.keys().downFromRack(), theme.leftX(), theme.active());
        drawLeftConditional(aircraft.racks().canRideRack(), "RideRack", McheliConfig.keys().putToRack(), theme.leftX(), theme.active());
        drawLeftConditional(aircraft.getVehicle() != null, "DismountRack", McheliConfig.keys().downFromRack(), theme.leftX(), theme.active());
    }

    /** Switching seats (passengers always, the pilot with free look held) and the gunner seats switch. */
    private void drawSeatKeys(AircraftEntity aircraft, int seatID, LayoutTheme theme) {
        var multipleSeats = aircraft.seats().count() > 1;
        var freeLookPressed = InputKey.isKeyDown(McheliConfig.keys().freeLook());

        if ((seatID > 0 && multipleSeats) || freeLookPressed) {
            int seatColor = seatID == 0 ? PILOT_SEAT_KEY_COLOR : theme.active();
            var prefix = seatID == 0 ? KeyNames.getDescOrName(McheliConfig.keys().freeLook()) + " + " : "";

            var nextMsg = "NextSeat : " + prefix + KeyNames.getDescOrName(McheliConfig.keys().gui());
            drawString(nextMsg, theme.rightX(), currentRightY, seatColor);
            currentRightY += 10;

            var prevMsg = "PrevSeat : " + prefix + KeyNames.getDescOrName(McheliConfig.keys().extra());
            drawString(prevMsg, theme.rightX(), currentRightY, seatColor);
            currentRightY += 10;
        }

        var gunnerStatus = aircraft.seats().gunnersAllowed() ? "ON" : "OFF";
        var gunnerMsg = "Gunner %s : %s + %s".formatted(
                gunnerStatus,
                KeyNames.getDescOrName(McheliConfig.keys().freeLook()),
                KeyNames.getDescOrName(McheliConfig.keys().cameraMode())
        );
        drawString(gunnerMsg, theme.leftX(), currentLeftY, theme.active());
        currentLeftY += 10;
    }

    /** Switching weapons and their modes, and the search light or camera mode key. */
    private void drawWeaponAndCameraKeys(AircraftEntity aircraft, Player player, int seatID, LayoutTheme theme) {
        var weaponSet = aircraft.weapons().current(player);
        if (aircraft.weapons().count() > 1) {
            drawLeftKey("Weapon", McheliConfig.keys().switchWeapon2(), theme.leftX(), theme.active());
        }

        if (weaponSet != null && weaponSet.currentMount() != null && weaponSet.currentMount().stats().modes() > 0) {
            drawLeftKey("WeaponMode", McheliConfig.keys().switchWeaponMode(), theme.leftX(), theme.active());
        }

        if (aircraft.searchLight().canSwitch(player)) {
            drawLeftKey("SearchLight", McheliConfig.keys().cameraMode(), theme.leftX(), theme.active());
        } else if (aircraft.cameras().canSwitchMode(seatID)) {
            drawLeftKey("CameraMode", McheliConfig.keys().cameraMode(), theme.leftX(), theme.active());
        }
    }

    protected void drawLeftKey(String label, int key, int x, int color) {
        var keyName = KeyNames.getDescOrName(key);
        drawString(label + " : " + keyName, x, currentLeftY, color);
        currentLeftY += 10;
    }

    protected void drawRightKey(String label, int key, int x, int color) {
        var keyName = KeyNames.getDescOrName(key);
        drawString(label + " : " + keyName, x, currentRightY, color);
        currentRightY += 10;
    }

    protected void drawLeftConditional(boolean condition, String label, int key, int x, int color) {
        if (condition) {
            drawLeftKey(label, key, x, color);
        }
    }

    public record LayoutTheme(int leftX, int rightX, int active, int inactive) {
    }
}
