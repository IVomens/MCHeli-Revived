package com.ivomens.mcheli.helicopter;

import com.ivomens.mcheli.aircraft.Rotor;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.vehicle.component.EngineComponent;
import com.ivomens.mcheli.vehicle.component.StateSink;
import com.ivomens.mcheli.vehicle.component.SyncedValue;
import com.ivomens.mcheli.vehicle.component.TickOrder;
import com.ivomens.mcheli.vehicle.component.TickPhase;
import com.ivomens.mcheli.vehicle.component.TickContext;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import com.ivomens.mcheli.vehicle.component.VehicleNbtCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * The main and tail rotors: how far they have turned, and folding the blades for storage. The fold
 * state is synced; the crew's fold request sets the transitional states on the server, and every
 * side plays the animation until it settles.
 */
@TickOrder(phase = TickPhase.PHYSICS)
public final class RotorBladesComponent implements VehicleComponent {

    public static final byte FOLDED = 0;
    public static final byte FOLDING = 1;
    public static final byte UNFOLDED = 2;
    public static final byte UNFOLDING = 3;
    /** Ticks after a fold state change before the client accepts the next fold request. */
    private static final int FOLD_COOLDOWN = 40;

    private final HelicopterEntity heli;
    private final SyncedValue<Byte> foldState;
    private final EngineComponent engine;
    private Rotor[] rotors = new Rotor[0];
    private double angle;
    private double prevAngle;
    private byte lastFoldState = -1;
    private int foldCooldown;
    private boolean startedFolded;

    RotorBladesComponent(HelicopterEntity heli, SyncedValue<Byte> foldState, EngineComponent engine) {
        this.heli = heli;
        this.foldState = foldState;
        this.engine = engine;
    }

    /** Client: no fold requests until the server's fold state has arrived. */
    void waitForServerFoldState() {
        this.foldCooldown = FOLD_COOLDOWN;
    }

    void build(VehicleSpec info) {
        this.rotors = new Rotor[info.kindParts().heliRotors().size()];
        for (int i = 0; i < this.rotors.length; i++) {
            VehicleParts.HeliRotor rotor = info.kindParts().heliRotors().get(i);
            this.rotors[i] = new Rotor(rotor.bladeNum, rotor.bladeRot, 2,
                    (float) rotor.pos.x, (float) rotor.pos.y, (float) rotor.pos.z,
                    (float) rotor.rot.x, (float) rotor.rot.y, (float) rotor.rot.z,
                    rotor.haveFoldFunc);
        }
    }

    public Rotor[] rotors() {
        return this.rotors;
    }

    /** How far the rotors have turned, in degrees. */
    public double angle() {
        return this.angle;
    }

    public double prevAngle() {
        return this.prevAngle;
    }

    private boolean hasRotors() {
        return this.heli.helicopterSpec() != null && this.rotors.length > 0;
    }

    public boolean canFold() {
        return hasRotors() && this.heli.helicopterSpec().file().kindFeatures().foldableBlades();
    }

    public boolean isFolded() {
        return hasRotors() && state() == FOLDED;
    }

    /** Folding or unfolding needs a stopped rotor, a settled fold state and no recent switch. */
    public boolean canSwitch() {
        byte state = state();
        return canFold() && this.engine.throttle() <= 0.01 && this.foldCooldown == 0
                && (state == UNFOLDED || state == FOLDED);
    }

    /** The rotors only produce lift when fully unfolded and not mid-animation. */
    public boolean canLift() {
        if (this.heli.helicopterSpec() == null) {
            return false;
        }
        if (this.rotors.length == 0) {
            return true;
        }
        if (state() != UNFOLDED) {
            return false;
        }
        for (Rotor rotor : this.rotors) {
            if (rotor.isFoldingOrUnfolding()) {
                return false;
            }
        }
        return true;
    }

    /** Folded blades that can taxi: the throttle keys roll the helicopter along the ground. */
    public boolean areStowed() {
        return canFold() && state() == FOLDED;
    }

    /** Starts folding: the engine stops and the blades swing back. */
    public void fold() {
        if (!hasRotors()) {
            return;
        }
        this.engine.setThrottle(0.0);
        for (Rotor rotor : this.rotors) {
            rotor.startFold();
        }
    }

    public void unfold() {
        if (this.heli.helicopterSpec() == null) {
            return;
        }
        for (Rotor rotor : this.rotors) {
            rotor.startUnfold();
        }
    }

    /** Snaps the blades into the folded position without animating. */
    public void forceFold() {
        if (!canFold()) {
            return;
        }
        for (Rotor rotor : this.rotors) {
            rotor.update((float) this.angle);
            fold();
            rotor.forceFold();
        }
    }

    /** Helicopters loaded onto a carrier rack fold their blades. */
    void foldOnRack(Entity carrier) {
        if (carrier instanceof SeatEntity && canFold()) {
            forceFold();
            setState(FOLDED);
        }
    }

    /** Client, once: blades that are folded (or folding) on the server start folded here. */
    void startFoldedIfServerIs() {
        if (this.startedFolded) {
            return;
        }
        this.startedFolded = true;
        byte state = state();
        if (state == FOLDING || state == FOLDED) {
            forceFold();
        }
    }

    public byte state() {
        return this.foldState.get();
    }

    @Override
    public void describe(StateSink state) {
        String[] names = {"folded", "folding", "unfolded", "unfolding"};
        byte fold = state();
        state.put("fold state", fold >= 0 && fold < names.length ? names[fold] : Byte.toString(fold));
        state.put("rotors", this.rotors.length);
        state.put("angle", this.angle);
        state.put("fold cooldown", this.foldCooldown);
    }

    /** Server: sets the synced fold state. */
    public void setState(byte state) {
        if (state >= FOLDED && state <= UNFOLDING) {
            this.foldState.set(state);
        }
    }

    @Override
    public void serverTick(TickContext ctx) {
        animate(false);
    }

    @Override
    public void clientTick(TickContext ctx) {
        animate(true);
    }

    /** Starts fold animations requested through the synced state and settles the state once they finish. */
    private void animate(boolean onClient) {
        byte state = state();
        if (state != this.lastFoldState) {
            if (state == FOLDING) {
                fold();
            } else if (state == UNFOLDING) {
                unfold();
            }
            if (onClient) {
                this.foldCooldown = FOLD_COOLDOWN;
            }
            this.lastFoldState = state;
        } else if (this.foldCooldown > 0) {
            this.foldCooldown--;
        }

        boolean animationDone = true;
        for (Rotor rotor : this.rotors) {
            rotor.update((float) this.angle);
            if (rotor.isFoldingOrUnfolding()) {
                animationDone = false;
            }
        }
        if (animationDone) {
            if (state == FOLDING) {
                setState(FOLDED);
            } else if (state == UNFOLDING) {
                setState(UNFOLDED);
            }
        }
    }

    /** Rotor speed rises quickly at low throttle and saturates near full. */
    void spin() {
        this.prevAngle = this.angle;
        this.angle += (1.0 - Math.pow(1.0 - this.engine.throttle(), 5.0)) * this.heli.spec().rotorSpeed();
        this.angle %= 360.0;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putDouble(VehicleNbtCodec.ROTOR_SPEED, this.engine.throttle());
        tag.putDouble(VehicleNbtCodec.ROTOR_ANGLE, this.angle);
        tag.putBoolean(VehicleNbtCodec.FOLDED_BLADES, state() == FOLDED);
    }

    @Override
    public void load(CompoundTag tag) {
        this.engine.setThrottle(tag.getDouble(VehicleNbtCodec.ROTOR_SPEED));
        this.angle = tag.getDouble(VehicleNbtCodec.ROTOR_ANGLE);
        setState(tag.getBoolean(VehicleNbtCodec.FOLDED_BLADES) ? FOLDED : UNFOLDED);
    }

    /** After loading: blades the save has folded snap into place. */
    void settleAfterLoad(boolean wasFolded) {
        if (!wasFolded && state() == FOLDED) {
            forceFold();
        }
        this.prevAngle = this.angle;
    }
}
