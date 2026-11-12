package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.entityview.TowChain;

import javax.annotation.Nullable;

/**
 * Tow chains: the chain this vehicle pulls another entity with, and the chain it is being pulled by.
 * The chains belong to the chain entities; the vehicle only remembers them.
 */
public final class TowComponent implements VehicleComponent {

    @Nullable
    private TowChain towChain;
    @Nullable
    private TowChain towedByChain;

    @Nullable
    public TowChain towChain() {
        return this.towChain;
    }

    public void setTowChain(@Nullable TowChain chain) {
        this.towChain = chain;
    }

    @Nullable
    public TowChain towedByChain() {
        return this.towedByChain;
    }

    public void setTowedByChain(@Nullable TowChain chain) {
        this.towedByChain = chain;
    }

    @Override
    public void describe(StateSink state) {
        state.put("towing", this.towChain != null);
        state.put("towed", this.towedByChain != null);
    }

    /** Forgets the tow chain once it is gone. */
    public void forgetRemovedChain() {
        if (this.towChain != null && this.towChain.entity().isRemoved()) {
            this.towChain = null;
        }
    }

    /** The vehicle is destroyed: its own tow chain goes with it. */
    public void discardChain() {
        if (this.towChain != null) {
            this.towChain.entity().discard();
            this.towChain = null;
        }
    }
}
