package com.ivomens.mcheli.equipment.gltd.client;

import com.ivomens.mcheli.aircraft.AircraftCamera;
import com.ivomens.mcheli.config.client.KeyNames;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import com.ivomens.mcheli.util.math.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Sight of the ground laser target designator: zoom, CAS weapon status and the lased position. */
public class GltdScreen extends HudOverlay {

    private static final int TEXT_COLOR = 0xCFFFFFFF;
    private static final int SIGHT_COLOR = 0xCF1FFF1F;
    private static final int DANGER_COLOR = 0xCFDF0408;
    private static final int DISABLED_COLOR = 0xCF7F7F7F;
    private static final float MAX_ZOOM = 10.0F;
    private static final double LASER_RANGE = 80.0;
    /** Closer targets are drawn in the danger colour. */
    private static final double DANGER_CLOSE = 20.0;
    /** Config code of the left shift key, which also dismounts. */
    private static final int KEY_LSHIFT = 42;

    public GltdScreen(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public boolean isDrawGui(Player player) {
        return player.getVehicle() instanceof GltdEntity;
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (isThirdPersonView && !McheliConfig.hud().displayInThirdPerson()) return;
        this.lineWidth(scaleFactor);
        if (!(player.getVehicle() instanceof GltdEntity gltd)) return;

        if (gltd.camera.getMode(0) == AircraftCamera.MODE_NIGHTVISION) {
            this.drawScreenNoise("textures/gui/alpha.png", 0.0F, 1.0F, 0.0F, 0.3F);
        }

        this.drawString(String.format("x%.1f", gltd.camera.getCameraZoom()), this.centerX - 70, this.centerY + 10,
                TEXT_COLOR);
        this.drawString(GltdEntity.STRIKE_NAME, this.centerX - 200, this.centerY + 65,
                gltd.countWait() == 0 ? SIGHT_COLOR : DANGER_COLOR);
        this.drawCommonPosition(gltd, SIGHT_COLOR);
        this.drawString(gltd.camera.getModeName(0), this.centerX + 30, this.centerY - 50, SIGHT_COLOR);
        this.drawSight(SIGHT_COLOR);
        this.drawTargetPosition(gltd, SIGHT_COLOR, DANGER_COLOR);
        this.drawKeyBind(gltd.camera, TEXT_COLOR, DISABLED_COLOR);
    }

    public void drawKeyBind(AircraftCamera camera, int color, int colorCannotUse) {
        int labelX = this.centerX + 55;
        int keyX = labelX + 60;
        int y = this.centerY + 40;
        int zoomInColor = camera.getCameraZoom() < MAX_ZOOM ? color : colorCannotUse;
        int zoomOutColor = camera.getCameraZoom() > 1.0F ? color : colorCannotUse;
        this.drawString("DISMOUNT :", labelX, y, color);
        this.drawString("CAM MODE :", labelX, y + 10, color);
        this.drawString("ZOOM IN   :", labelX, y + 20, zoomInColor);
        this.drawString("ZOOM OUT :", labelX, y + 30, zoomOutColor);
        this.drawString(KeyNames.getDescOrName(KEY_LSHIFT) + " or " + KeyNames.getDescOrName(McheliConfig.keys().unmount()),
                keyX, y, color);
        this.drawString(KeyNames.getDescOrName(McheliConfig.keys().cameraMode()), keyX, y + 10, color);
        this.drawString(KeyNames.getDescOrName(McheliConfig.keys().zoom()), keyX, y + 20, zoomInColor);
        this.drawString(KeyNames.getDescOrName(McheliConfig.keys().switchWeaponMode()), keyX, y + 30, zoomOutColor);
    }

    public void drawCommonPosition(GltdEntity gltd, int color) {
        Entity riddenByEntity = gltd.getRiddenByEntity();
        this.drawString(String.format("X: %+.1f", gltd.getX()), this.centerX - 145, this.centerY, color);
        this.drawString(String.format("Y: %+.1f", gltd.getY()), this.centerX - 145, this.centerY + 10, color);
        this.drawString(String.format("Z: %+.1f", gltd.getZ()), this.centerX - 145, this.centerY + 20, color);
        this.drawString(String.format("AX: %+.1f", riddenByEntity.getYRot()), this.centerX - 145, this.centerY + 40,
                color);
        this.drawString(String.format("AY: %+.1f", riddenByEntity.getXRot()), this.centerX - 145, this.centerY + 50,
                color);
    }

    public void drawTargetPosition(GltdEntity gltd, int color, int colorDanger) {
        Entity operator = gltd.getRiddenByEntity();
        if (operator == null) return;

        AircraftCamera camera = gltd.camera;
        Vec3 from = new Vec3(camera.posX(), camera.posY(), camera.posZ());
        Vec3 to = from.add(Rotations.direction(operator.getYRot(), operator.getXRot()).normalize().scale(LASER_RANGE));
        BlockHitResult hit = operator.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, operator));
        int textX = this.centerX + 50;
        int textY = this.centerY - 5;
        if (hit.getType() == HitResult.Type.MISS) {
            this.drawString("X: --.--m", textX, textY - 15, color);
            this.drawString("Y: --.--m", textX, textY, color);
            this.drawString("Z: --.--m", textX, textY + 15, color);
            this.drawCenteredString("[--.--m]", this.centerX, this.centerY + 30, colorDanger);
            return;
        }
        Vec3 target = hit.getLocation();
        this.drawString(String.format("X: %+.2fm", target.x), textX, textY - 15, color);
        this.drawString(String.format("Y: %+.2fm", target.y), textX, textY, color);
        this.drawString(String.format("Z: %+.2fm", target.z), textX, textY + 15, color);
        double distance = target.distanceTo(from);
        this.drawCenteredString(String.format("[%.2fm]", distance), this.centerX, this.centerY + 30,
                distance > DANGER_CLOSE ? color : colorDanger);
    }

    /** Four corner brackets around the crosshair. */
    private void drawSight(int color) {
        double x = this.centerX;
        double y = this.centerY;
        double[] lines = new double[32];
        int i = 0;
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                double cornerX = x + 30.0 * sideX;
                double cornerY = y + 20.0 * sideY;
                i = putSegment(lines, i, cornerX, y + 10.0 * sideY, cornerX, cornerY);
                i = putSegment(lines, i, cornerX, cornerY, x + 10.0 * sideX, cornerY);
            }
        }
        this.drawLine(lines, color);
    }

    private static int putSegment(double[] lines, int index, double x1, double y1, double x2, double y2) {
        lines[index] = x1;
        lines[index + 1] = y1;
        lines[index + 2] = x2;
        lines[index + 3] = y2;
        return index + 4;
    }
}
