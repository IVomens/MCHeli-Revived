package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.util.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Gunner camera of a vehicle seat: its own rotation and zoom, plus the vision mode per seat (normal,
 * night vision, thermal). Night and thermal vision grant the viewer a short night vision effect and,
 * on the client, switch the post-processing shader.
 */
public class AircraftCamera {

    public static final int MODE_NORMAL = 0;
    public static final int MODE_NIGHTVISION = 1;
    public static final int MODE_THERMALVISION = 2;
    private static final ResourceLocation NIGHTVISION = Ids.of("shaders/post/nightvision.json");
    private static final ResourceLocation THERMAL = Ids.of("shaders/post/thermal.json");

    /** Night vision granted for a vision mode lasts this long and is refreshed before it runs out. */
    private static final int NIGHT_VISION_TICKS = 250;
    private static final int NIGHT_VISION_REFRESH_BELOW = 500;
    private static final int SEAT_COUNT = 2;

    private final Level level;
    private double posX;
    private double posY;
    private double posZ;
    private float rotationYaw;
    private float rotationPitch;
    private float prevRotationYaw;
    private float prevRotationPitch;
    private float partRotationYaw;
    private float partRotationPitch;
    private float prevPartRotationYaw;
    private float prevPartRotationPitch;
    private float zoom = 1.0F;
    private final int[] mode = new int[SEAT_COUNT];
    private final boolean[] canUseShader = new boolean[SEAT_COUNT];
    private final int[] lastMode = new int[SEAT_COUNT];

    public float partRotationPitch() {
        return this.partRotationPitch;
    }

    public float partRotationYaw() {
        return this.partRotationYaw;
    }

    public double posX() {
        return this.posX;
    }

    public double posY() {
        return this.posY;
    }

    public double posZ() {
        return this.posZ;
    }

    public float prevPartRotationPitch() {
        return this.prevPartRotationPitch;
    }

    public float prevPartRotationYaw() {
        return this.prevPartRotationYaw;
    }

    public float rotationPitch() {
        return this.rotationPitch;
    }

    public float rotationYaw() {
        return this.rotationYaw;
    }

    public void setPartRotationPitch(float partRotationPitch) {
        this.partRotationPitch = partRotationPitch;
    }

    public void setPartRotationYaw(float partRotationYaw) {
        this.partRotationYaw = partRotationYaw;
    }

    public void setPrevPartRotationPitch(float prevPartRotationPitch) {
        this.prevPartRotationPitch = prevPartRotationPitch;
    }

    public void setPrevPartRotationYaw(float prevPartRotationYaw) {
        this.prevPartRotationYaw = prevPartRotationYaw;
    }

    public void setPrevRotationPitch(float prevRotationPitch) {
        this.prevRotationPitch = prevRotationPitch;
    }

    public void setPrevRotationYaw(float prevRotationYaw) {
        this.prevRotationYaw = prevRotationYaw;
    }

    public void setRotationPitch(float rotationPitch) {
        this.rotationPitch = rotationPitch;
    }

    public void setRotationYaw(float rotationYaw) {
        this.rotationYaw = rotationYaw;
    }

    /** @param owner unused; kept for the existing callers */
    public AircraftCamera(Level level, Entity owner) {
        this.level = level;
    }

    public AircraftCamera(Level level, Entity owner, double x, double y, double z) {
        this(level, owner);
        this.setPosition(x, y, z);
    }

    public void initCamera(int uid, @Nullable Entity viewer) {
        this.setCameraZoom(1.0F);
        this.setMode(uid, MODE_NORMAL);
        this.updateViewer(uid, viewer);
    }

    public void setMode(int uid, int newMode) {
        if (!this.isValidUid(uid)) return;
        this.mode[uid] = newMode < 0 ? 0 : newMode % this.getModeNum(uid);

        if (!this.level.isClientSide()) return;
        LocalClient.get().useVisionEffect(switch (this.mode[uid]) {
            case MODE_NIGHTVISION -> NIGHTVISION;
            case MODE_THERMALVISION -> THERMAL;
            default -> null;
        });
    }

    public void setShaderSupport(int uid, Boolean supported) {
        if (this.isValidUid(uid)) {
            this.setMode(uid, MODE_NORMAL);
            this.canUseShader[uid] = supported;
        }
    }

    public boolean isValidUid(int uid) {
        return uid >= 0 && uid < SEAT_COUNT;
    }

    /** Thermal vision needs shader support on the seat's client. */
    private int getModeNum(int uid) {
        return this.isValidUid(uid) && this.canUseShader[uid] ? 3 : 2;
    }

    public int getMode(int uid) {
        return this.isValidUid(uid) ? this.mode[uid] : MODE_NORMAL;
    }

    public String getModeName(int uid) {
        return switch (this.getMode(uid)) {
            case MODE_NIGHTVISION -> "NIGHT VISION";
            case MODE_THERMALVISION -> "THERMAL VISION";
            default -> "";
        };
    }

    public void updateViewer(int uid, @Nullable Entity viewer) {
        if (!this.isValidUid(uid) || viewer == null) return;
        int currentMode = this.getMode(uid);
        if (viewer instanceof LivingEntity living && living.isAlive()) {
            MobEffectInstance nightVision = living.getEffect(MobEffects.NIGHT_VISION);
            // Only remove a short effect, which must be ours, not a potion the player drank.
            if (currentMode == MODE_NORMAL && this.lastMode[uid] != MODE_NORMAL && nightVision != null
                    && nightVision.getDuration() > 0 && nightVision.getDuration() < NIGHT_VISION_REFRESH_BELOW) {
                living.removeEffect(MobEffects.NIGHT_VISION);
            }
            if (currentMode != MODE_NORMAL && !living.level().isClientSide()
                    && (nightVision == null || nightVision.getDuration() < NIGHT_VISION_REFRESH_BELOW)) {
                living.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false));
            }
        }
        this.lastMode[uid] = currentMode;
    }

    public void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public float getCameraZoom() {
        return this.zoom;
    }

    public void setCameraZoom(float zoom) {
        this.zoom = Math.max(zoom, 1.0F);
    }
}
