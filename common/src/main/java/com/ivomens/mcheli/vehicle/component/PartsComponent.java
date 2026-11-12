package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.Parts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.util.Terrain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * The two-state parts every vehicle type can have: canopy, hatch and landing gear, plus the weapon
 * bay doors. Their states are bits of one synced word; planes add nozzles and wings to the same word.
 * Switching a part starts a short cooldown that the plane's nozzles and wings share.
 */
@TickOrder(phase = TickPhase.FOLLOW_HULL)
public final class PartsComponent implements VehicleComponent {

    /** Bits of the synced part word; 1 and 3 belong to the plane's nozzle and wing. */
    public static final int BIT_CANOPY = 0;
    public static final int BIT_NOZZLE = 1;
    public static final int BIT_LANDING_GEAR = 2;
    public static final int BIT_WING = 3;
    public static final int BIT_HATCH = 4;
    public static final int MODE_SWITCH_COOLDOWN = 20;
    private static final float DEFAULT_GEAR_UNFOLD_THROTTLE = 0.8F;
    /** Part bits that are saved with the vehicle: the folded wings (planes and ships) and the hatch. */
    private static final int SAVED_BITS = 1 << BIT_WING | 1 << BIT_HATCH;

    private final AircraftEntity vehicle;
    private final EntityDataAccessor<Integer> key;
    private final SyncedValue<Integer> status;
    private final EngineComponent engine;
    private Runnable onHatchClosed = () -> {
    };
    @Nullable
    private Parts hatch;
    @Nullable
    private Parts canopy;
    @Nullable
    private Parts landingGear;
    private WeaponBayDoor[] weaponBays = new WeaponBayDoor[0];
    /** Parts the vehicle type adds, such as a plane's nozzles and wings; animated after the shared ones. */
    private final List<Parts> typeParts = new ArrayList<>();
    private Runnable afterServerAnimation = () -> {
    };
    private DoubleSupplier gearUnfoldThrottle = () -> DEFAULT_GEAR_UNFOLD_THROTTLE;
    private IntSupplier vtolMode = () -> 0;
    private int modeSwitchCooldown;

    public PartsComponent(AircraftEntity vehicle, EntityDataAccessor<Integer> key, EngineComponent engine) {
        this.vehicle = vehicle;
        this.key = key;
        this.status = new SyncedValue<>(vehicle.getEntityData(), key);
        this.engine = engine;
    }

    /** Runs when the hatch is told to close, which ends a crew drop through it. */
    public void whenHatchCloses(Runnable listener) {
        this.onHatchClosed = listener;
    }

    /** A new part in the synced word, for the vehicle types that add their own; it animates with the rest. */
    public Parts newPart(int bit, String name) {
        Parts part = part(bit, name);
        this.typeParts.add(part);
        return part;
    }

    private Parts part(int bit, String name) {
        return new Parts(this.vehicle, bit, this.key, name);
    }

    /** Server: the vehicle type's own rule for its parts, after they have moved. */
    public void afterServerAnimation(Runnable rule) {
        this.afterServerAnimation = rule;
    }

    /** Below this throttle, close to the ground, the gear comes down by itself; 0.8 unless the type says. */
    public void unfoldGearBelowThrottle(DoubleSupplier throttle) {
        this.gearUnfoldThrottle = throttle;
    }

    /** VTOL vehicles lower the gear when hovering; see {@link #updateAutomaticLandingGear}. */
    public void followVtolMode(IntSupplier mode) {
        this.vtolMode = mode;
    }

    /** Server: moves every part a step, then lowers or retracts the gear as the flight calls for. */
    @Override
    public void serverTick(TickContext ctx) {
        if (this.vehicle.spec() == null || this.vehicle.isDestroyed()) {
            return;
        }
        int status = statusWord();
        animate(status);
        updateAutomaticLandingGear();
        animateTypeParts(status);
        this.afterServerAnimation.run();
    }

    /** Client: the parts follow the synced state. */
    @Override
    public void clientTick(TickContext ctx) {
        if (this.vehicle.spec() == null || this.vehicle.isDestroyed()) {
            return;
        }
        int status = statusWord();
        animate(status);
        animateTypeParts(status);
    }

    private void animateTypeParts(int status) {
        for (Parts part : this.typeParts) {
            part.updateStatusClient(status);
            part.update();
        }
    }

    /** Recreates the parts the definition asks for. */
    public void rebuild(VehicleSpec info) {
        this.typeParts.clear();
        this.hatch = info.parts().haveHatch() ? createHatch() : null;
        this.canopy = info.parts().haveCanopy() ? createCanopy() : null;
        this.landingGear = info.parts().haveLandingGear() ? createLandingGear() : null;
        this.weaponBays = new WeaponBayDoor[info.parts().weaponBays().size()];
        for (int i = 0; i < this.weaponBays.length; i++) {
            this.weaponBays[i] = new WeaponBayDoor();
        }
    }

    private Parts createHatch() {
        Parts part = part(BIT_HATCH, "Hatch");
        part.setRotationMax(90.0F);
        part.setRotationInv(1.5F);
        part.setOnSounds(Parts.Sound.NONE, new Parts.Sound("plane_cc", 1.0F, 1.0F));
        part.setOffSounds(Parts.Sound.NONE, new Parts.Sound("plane_cc", 1.0F, 1.0F));
        part.setSwitchingSound(new Parts.Sound("plane_cv", 1.0F, 0.5F));
        return part;
    }

    private Parts createCanopy() {
        Parts part = part(BIT_CANOPY, "Canopy");
        part.setRotationMax(90.0F);
        part.setRotationInv(3.5F);
        part.setOnSounds(Parts.Sound.NONE, new Parts.Sound("plane_cc", 1.0F, 1.0F));
        part.setOffSounds(Parts.Sound.NONE, new Parts.Sound("plane_cc", 1.0F, 1.0F));
        return part;
    }

    private Parts createLandingGear() {
        Parts part = part(BIT_LANDING_GEAR, "LandingGear");
        part.setRotationMax(90.0F);
        part.setRotationInv(2.5F);
        Parts.Sound click = new Parts.Sound("plane_cc", 1.0F, 0.5F);
        part.setOnSounds(click, click);
        part.setOffSounds(click, click);
        part.setSwitchingSound(new Parts.Sound("plane_cv", 1.0F, 0.75F));
        return part;
    }

    @Nullable
    public Parts hatch() {
        return this.hatch;
    }

    @Nullable
    public Parts canopy() {
        return this.canopy;
    }

    @Nullable
    public Parts landingGear() {
        return this.landingGear;
    }

    public WeaponBayDoor weaponBay(int index) {
        return this.weaponBays[index];
    }

    public int weaponBayCount() {
        return this.weaponBays.length;
    }

    public int statusWord() {
        return this.status.get();
    }

    public int modeSwitchCooldown() {
        return this.modeSwitchCooldown;
    }

    @Override
    public void describe(StateSink state) {
        state.put("status word", "0x" + Integer.toHexString(statusWord()));
        state.put("canopy", this.canopy == null ? "-" : StateSink.number(canopyRotation()) + " deg");
        state.put("landing gear", this.landingGear == null ? "-" : StateSink.number(landingGearRotation()) + " deg");
        state.put("hatch", this.hatch == null ? "-" : StateSink.number(hatchRotation()) + " deg");
        state.put("weapon bays", this.weaponBays.length);
        state.put("mode switch cooldown", this.modeSwitchCooldown);
    }

    public void setModeSwitchCooldown(int ticks) {
        this.modeSwitchCooldown = ticks;
    }

    public void tickCooldown() {
        if (this.modeSwitchCooldown > 0) {
            this.modeSwitchCooldown--;
        }
    }

    /** Takes the synced state (clients) and moves every part a step. */
    private void animate(int status) {
        for (Parts part : new Parts[]{this.hatch, this.canopy, this.landingGear}) {
            if (part != null) {
                part.updateStatusClient(status);
                part.update();
            }
        }
    }

    /**
     * Server: float planes retract the gear on water; everything lowers it again when slow and close to
     * the ground (but not over water for float planes), and VTOLs in hover mode always do.
     */
    private void updateAutomaticLandingGear() {
        AircraftEntity ac = this.vehicle;
        if (this.landingGear == null) {
            return;
        }
        VehicleSpec info = ac.spec();
        float gearFactor = this.landingGear.getFactor();
        boolean folded = isLandingGearFolded();
        if (!folded && gearFactor <= 0.1F) {
            boolean groundBelow = Terrain.hasBlockY(ac, 3, -20);
            boolean fold = (this.engine.throttle() <= 0.8F || ac.onGround() || groundBelow)
                    && info != null && info.file().physics().canFloat()
                    && (ac.isInWater() || Terrain.firstBlockY(ac, 3, -20, true) == Blocks.WATER);
            if (fold) {
                this.landingGear.setStatusServer(true);
            }
        } else if (folded && gearFactor >= 0.9F) {
            boolean groundBelow = Terrain.hasBlockY(ac, 3, -10);
            boolean unfold = false;
            if (this.engine.throttle() < this.gearUnfoldThrottle.getAsDouble() && groundBelow) {
                unfold = true;
                if (info != null && info.file().physics().canFloat()) {
                    Block surface = Terrain.firstBlockY(ac.level(), ac.getX(), ac.getY() + 1.0
                            + info.file().physics().floatOffset(),
                            ac.getZ(), 1, -150, true);
                    if (surface == Blocks.WATER) {
                        unfold = false;
                    }
                }
            } else if (this.vtolMode.getAsInt() == 2 && groundBelow) {
                unfold = true;
            }
            if (unfold) {
                this.landingGear.setStatusServer(false);
            }
        }
    }

    public boolean hasHatch() {
        return this.hatch != null;
    }

    public boolean canUnfoldHatch() {
        return this.hatch != null && this.modeSwitchCooldown <= 0 && this.hatch.isOFF();
    }

    public boolean canFoldHatch() {
        return this.hatch != null && this.modeSwitchCooldown <= 0 && this.hatch.isON();
    }

    public void foldHatch(boolean fold) {
        foldHatch(fold, false);
    }

    /** Opens ({@code fold}) or closes the hatch; {@code force} ignores the switch cooldown. */
    public void foldHatch(boolean fold, boolean force) {
        if (this.hatch == null || !force && this.modeSwitchCooldown > 0) {
            return;
        }
        this.hatch.setStatusServer(fold);
        this.modeSwitchCooldown = MODE_SWITCH_COOLDOWN;
        if (!fold) {
            this.onHatchClosed.run();
        }
    }

    public float hatchRotation() {
        return this.hatch != null ? this.hatch.rotation() : 0.0F;
    }

    public float prevHatchRotation() {
        return this.hatch != null ? this.hatch.prevRotation() : 0.0F;
    }

    public void foldLandingGear() {
        if (this.landingGear != null && this.modeSwitchCooldown <= 0) {
            this.landingGear.setStatusServer(true);
            this.modeSwitchCooldown = MODE_SWITCH_COOLDOWN;
        }
    }

    public void unfoldLandingGear() {
        if (this.landingGear != null && this.modeSwitchCooldown <= 0 && isLandingGearFolded()) {
            this.landingGear.setStatusServer(false);
            this.modeSwitchCooldown = MODE_SWITCH_COOLDOWN;
        }
    }

    /** The gear can only be raised when fully down and with nothing below for 10 blocks. */
    public boolean canFoldLandingGear() {
        return landingGearRotation() < 1.0F && !isLandingGearFolded()
                && Terrain.firstBlockY(this.vehicle, 3, -10, true) == Blocks.AIR;
    }

    public boolean canUnfoldLandingGear() {
        return landingGearRotation() >= 89.0F && isLandingGearFolded();
    }

    public boolean isLandingGearFolded() {
        return this.landingGear != null && this.landingGear.getStatus();
    }

    public float landingGearRotation() {
        return this.landingGear != null ? this.landingGear.rotation() : 0.0F;
    }

    public float prevLandingGearRotation() {
        return this.landingGear != null ? this.landingGear.prevRotation() : 0.0F;
    }

    public void openCanopy() {
        if (this.canopy != null && this.modeSwitchCooldown <= 0) {
            this.canopy.setStatusServer(true);
            this.modeSwitchCooldown = MODE_SWITCH_COOLDOWN;
        }
    }

    /** Blows the canopy open for an ejection, regardless of the cooldown. */
    public void openCanopyForEjection() {
        if (this.canopy != null) {
            this.canopy.setStatusServer(true, false);
            this.modeSwitchCooldown = 2 * MODE_SWITCH_COOLDOWN;
        }
    }

    public void closeCanopy() {
        if (this.canopy != null && this.modeSwitchCooldown <= 0 && isCanopyOpen()) {
            this.canopy.setStatusServer(false);
            this.modeSwitchCooldown = MODE_SWITCH_COOLDOWN;
        }
    }

    public boolean isCanopyOpen() {
        return this.canopy != null && this.canopy.getStatus();
    }

    public boolean isCanopyClosed() {
        return this.canopy == null || !isCanopyOpen() && canopyRotation() <= 0.01F;
    }

    public float canopyRotation() {
        return this.canopy != null ? this.canopy.rotation() : 0.0F;
    }

    public float prevCanopyRotation() {
        return this.canopy != null ? this.canopy.prevRotation() : 0.0F;
    }

    public void save(CompoundTag tag) {
        tag.putInt(VehicleNbtCodec.PART_STATUS, statusWord() & SAVED_BITS);
    }

    public void load(CompoundTag tag) {
        this.status.set(tag.getInt(VehicleNbtCodec.PART_STATUS));
    }
}
