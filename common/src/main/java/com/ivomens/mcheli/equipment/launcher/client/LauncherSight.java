package com.ivomens.mcheli.equipment.launcher.client;

import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.render.ModTextures;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.config.client.KeyNames;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.equipment.launcher.LightWeaponItem;
import com.ivomens.mcheli.client.overlay.HudCanvas.LineMode;
import com.ivomens.mcheli.client.overlay.HudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Sight of the hand-held launchers: the Stinger scope or the Javelin (FGM-148) command launch unit. */
public class LauncherSight extends HudOverlay {

    private static final int TEXT_COLOR = 0xCFFFFFFF;
    private static final int READY_COLOR = 0xFF28D448;
    private static final int LOCK_COLOR = 0xFFDF0408;
    private static final int RETICLE_COLOR = 0xA0FFFFFF;
    /** Top attack mode needs at least this much horizontal distance to the target. */
    private static final double TOP_ATTACK_MIN_DISTANCE = 40.0;
    /** The Javelin sight texture is laid out on a 1024x1024 grid. */
    private static final double JAVELIN_TEXTURE_SIZE = 1024.0;

    private final HandheldWeaponView launcher;

    public LauncherSight(Minecraft minecraft, HandheldWeaponView launcher) {
        super(minecraft);
        this.launcher = launcher;
    }

    @Override
    public boolean isDrawGui(Player player) {
        if (!LightWeaponItem.isHeld(player)) return false;
        Entity vehicle = player.getVehicle();
        return !(vehicle instanceof AircraftEntity) && !(vehicle instanceof GltdEntity);
    }

    @Override
    public void drawGui(Player player, boolean isThirdPersonView) {
        if (isThirdPersonView) return;
        this.lineWidth(scaleFactor);
        LockOnSeeker gs = this.launcher.seeker();
        if (!this.isDrawGui(player) || gs == null || this.launcher.weapon() == null) {
            return;
        }

        if (player.getEffect(MobEffects.NIGHT_VISION) != null) {
            this.drawNightVisionNoise();
        }

        this.enableBlend();
        boolean canFire = this.launcher.mode() == 0 || gs.lockCount() <= 0
                || horizontalDistance(player, gs.target()) >= TOP_ATTACK_MIN_DISTANCE;
        if ("fgm148".equalsIgnoreCase(LightWeaponItem.getLightWeaponName(player.getMainHandItem()))) {
            this.drawGuiFGM148(player, gs, canFire, player.getMainHandItem());
            this.drawKeyBind(TEXT_COLOR, true);
        } else {
            this.drawStingerScope();
            this.drawKeyBind(TEXT_COLOR, false);
        }
        this.disableBlend();

        this.drawLock(READY_COLOR, LOCK_COLOR, gs.lockCount(), gs.lockTicks());
        this.drawRange(player, gs, canFire, READY_COLOR, LOCK_COLOR);
    }

    private static double horizontalDistance(Player player, Entity target) {
        if (target == null) return 0.0;
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void drawStingerScope() {
        this.resetColor();
        ModTextures.bind("textures/gui/stinger.png");
        double size = 512.0;
        while (size < this.width || size < this.height) {
            size *= 2.0;
        }
        this.drawTexturedModalRectRotate(-(size - this.width) / 2.0, -(size - this.height) / 2.0 - 20.0,
                size, size, 0.0, 0.0, 256.0, 256.0, 0.0F);
    }

    public void drawNightVisionNoise() {
        this.drawScreenNoise("textures/gui/alpha.png", 0.0F, 1.0F, 0.0F, 0.3F);
    }

    /** Lock progress bar below the crosshair. */
    void drawLock(int frameColor, int barColor, int lockCount, int lockCountMax) {
        int left = this.centerX - 20;
        int top = this.centerY + 41;
        drawRect(left, top, left + 40, top + 5, frameColor);
        float progress = (float) lockCount / lockCountMax;
        drawRect(left + 1, top + 1, left + 1 + (int) (38.0 * progress), top + 4, barColor);
    }

    void drawRange(Player player, LockOnSeeker gs, boolean canFire, int readyColor, int notReadyColor) {
        String distance = "[--.--]";
        int color = notReadyColor;
        Entity target = gs.lockCount() > 0 ? gs.lockingTarget() : null;
        if (target != null) {
            distance = String.format("[%.2f]", horizontalDistance(player, target));
            color = canFire ? readyColor : notReadyColor;
            if (!McheliConfig.render().hideKeybind() && gs.isLockComplete()) {
                String key = KeyNames.getDescOrName(KeySettings.ATTACK);
                this.drawCenteredString("Shot : " + key, this.centerX, this.centerY + 65, TEXT_COLOR);
            }
        }
        this.drawCenteredString(distance, this.centerX, this.centerY + 50, color);
    }

    /** Javelin command launch unit: tracking gates around the target and the status lights. */
    void drawGuiFGM148(Player player, LockOnSeeker gs, boolean canFire, ItemStack itemStack) {
        this.resetColor();
        // Snap the sight size to a multiple of 64 pixels so the texture stays crisp.
        double scale = Math.min(this.width / 800.0, this.height / 700.0);
        int size = (int) (JAVELIN_TEXTURE_SIZE * scale) / 64 * 64;
        scale = size / JAVELIN_TEXTURE_SIZE;
        double left = (double) -(size - this.width) / 2;
        double top = (double) -(size - this.height) / 2 - 20;
        double right = left + size;
        double bottom = top + size;

        if (gs.lockCount() > 0) {
            drawTrackingGates(player, gs, scale, left, top, right, bottom);
        }

        // Black out everything around the sight.
        drawRect(-1, -1, (int) left + 1, this.height + 1, 0xFF000000);
        drawRect((int) right - 1, -1, this.width + 1, this.height + 1, 0xFF000000);
        drawRect(-1, -1, this.width + 1, (int) top + 1, 0xFF000000);
        drawRect(-1, (int) bottom - 1, this.width + 1, this.height + 1, 0xFF000000);
        this.enableBlend();
        this.resetColor();
        ModTextures.bind("textures/gui/javelin.png");
        this.drawTexturedModalRectRotate(left, top, size, size, 0.0, 0.0, 256.0, 256.0, 0.0F);

        ModTextures.bind("textures/gui/javelin2.png");
        boolean nightVision = player.getEffect(MobEffects.NIGHT_VISION) != null;
        int zoom = this.launcher.zoom();
        if (!nightVision) drawSightRegion(left, top, scale, 247, 211, 380, 350);
        if (player.getTicksUsingItem() <= 60) drawSightRegion(left, top, scale, 130, 334, 257, 455);
        if (zoom == 0) drawSightRegion(left, top, scale, 387, 211, 510, 350);
        if (zoom == this.launcher.weapon().spec().camera().zoom().size() - 1) {
            drawSightRegion(left, top, scale, 511, 211, 645, 350);
        }
        if (gs.lockCount() > 0) drawSightRegion(left, top, scale, 643, 211, 775, 350);
        if (this.launcher.mode() == 1) {
            drawSightRegion(left, top, scale, 768, 340, 890, 455);
        } else {
            drawSightRegion(left, top, scale, 768, 456, 890, 565);
        }
        if (!canFire) drawSightRegion(left, top, scale, 379, 670, 511, 810);
        if (itemStack.getDamageValue() >= itemStack.getMaxDamage()) drawSightRegion(left, top, scale, 512, 670, 645, 810);
        if (gs.lockCount() < gs.lockTicks()) drawSightRegion(left, top, scale, 646, 670, 776, 810);
        if (nightVision) drawSightRegion(left, top, scale, 768, 562, 890, 694);
    }

    /** The crosshair follows the target once half locked; the corner gates close in as the lock builds. */
    private void drawTrackingGates(Player player, LockOnSeeker gs, double scale, double left, double top,
                                   double right, double bottom) {
        Vec3 pos = this.launcher.markerPosition();
        if (pos == null) {
            pos = new Vec3((double) this.width / 2, (double) this.height / 2, 0.0);
        }
        double targetX = pos.x;
        double targetY = pos.y;
        double x = LegacyMath.limit(targetX, left + 280.0 * scale, right - 280.0 * scale);
        double y = LegacyMath.limit(targetY, top + 370.0 * scale, bottom - 370.0 * scale);
        if (gs.lockCount() >= gs.lockTicks() / 2) {
            this.drawLine(new double[]{-1.0, y, this.width + 1, y, x, -1.0, x, this.height + 1}, RETICLE_COLOR);
        }
        if (player.tickCount % 6 < 3) return;

        Vec3 boxPos = this.launcher.markerCorner();
        if (boxPos == null) {
            boxPos = new Vec3((double) this.width / 2 - 65, (double) this.height / 2 - 50, 0.0);
        }
        double remaining = 1.0 - (double) gs.lockCount() / gs.lockTicks();
        double gateX = LegacyMath.limit(Math.abs(targetX - boxPos.x), 25.0, 70.0);
        double gateY = LegacyMath.limit(Math.abs(targetY - boxPos.y), 15.0, 70.0);
        gateX += (70.0 - gateX) * remaining;
        gateY += (70.0 - gateY) * remaining;
        drawCorner(x - gateX, y - gateY, 10, 6);
        drawCorner(x + gateX, y - gateY, -10, 6);
        gateY /= 6.0;
        drawCorner(x - gateX, y + gateY, 10, -6);
        drawCorner(x + gateX, y + gateY, -10, -6);
    }

    private void drawCorner(double x, double y, double armX, double armY) {
        this.drawLine(new double[]{x, y + armY, x, y, x + armX, y}, RETICLE_COLOR, LineMode.LINE_STRIP);
    }

    /** Draws the region (u0, v0)-(u1, v1) of the 1024x1024 Javelin overlay at its place in the sight. */
    private void drawSightRegion(double left, double top, double scale, double u0, double v0, double u1, double v1) {
        this.drawTexturedRect(left + u0 * scale, top + v0 * scale, (u1 - u0) * scale, (v1 - v0) * scale, u0, v0,
                u1 - u0, v1 - v0, JAVELIN_TEXTURE_SIZE, JAVELIN_TEXTURE_SIZE);
    }

    public void drawKeyBind(int color, boolean canSwitchMode) {
        int labelX = this.centerX + 55;
        int keyX = labelX + 60;
        int y = this.centerY + 40;
        this.drawString("CAM MODE :", labelX, y + 10, color);
        this.drawString("ZOOM      :", labelX, y + 20, color);
        this.drawString(KeyNames.getDescOrName(McheliConfig.keys().cameraMode()), keyX, y + 10, color);
        this.drawString(KeyNames.getDescOrName(McheliConfig.keys().zoom()), keyX, y + 20, color);
        if (canSwitchMode) {
            this.drawString("MODE      :", labelX, y + 30, color);
            this.drawString(KeyNames.getDescOrName(McheliConfig.keys().switchWeaponMode()), keyX, y + 30, color);
        }
    }
}
