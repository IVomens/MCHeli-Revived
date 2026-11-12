package com.ivomens.mcheli.vehicle.component;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;

/**
 * The vehicle's roll, the one rotation vanilla entities do not have. The server publishes it to clients
 * in whole degrees whenever it turned by one; clients keep the previous roll to interpolate between ticks
 * (or frames, for the pilot's own vehicle).
 */
public final class RollComponent implements VehicleComponent {

    private final SynchedEntityData data;
    private final EntityDataAccessor<Integer> synced;
    private float roll;
    private float previous;

    /** @param synced the synced roll, in whole degrees */
    public RollComponent(SynchedEntityData data, EntityDataAccessor<Integer> synced) {
        this.data = data;
        this.synced = synced;
    }

    /** The roll in degrees. */
    public float get() {
        return this.roll;
    }

    public void set(float roll) {
        this.roll = roll;
    }

    /** Sets the roll with no interpolation from the old one, as loading and spawning do. */
    public void init(float roll) {
        set(roll);
        this.previous = roll;
    }

    /** The roll at the end of the last tick (or frame, for the pilot's own vehicle). */
    public float previous() {
        return this.previous;
    }

    public void setPrevious(float roll) {
        this.previous = roll;
    }

    /** The roll between the last two ticks. */
    public float interpolated(float partialTicks) {
        return this.previous + (this.roll - this.previous) * partialTicks;
    }

    /** The roll the server last published, in whole degrees. */
    public float synced() {
        return (short) (int) this.data.get(this.synced);
    }

    @Override
    public void describe(StateSink state) {
        state.put("roll", this.roll);
        state.put("previous", this.previous);
        state.put("synced", synced());
    }

    /** Server: clients learn the roll whenever it turns by a whole degree. */
    public void publish() {
        if ((int) this.previous != (int) this.roll) {
            this.data.set(this.synced, (int) Mth.wrapDegrees(this.roll));
        }
    }
}
