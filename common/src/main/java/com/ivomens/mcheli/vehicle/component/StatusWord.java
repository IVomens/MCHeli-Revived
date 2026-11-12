package com.ivomens.mcheli.vehicle.component;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * The vehicle's synced status bits (flare out, free look, search light, held keys...). Several
 * components share it, each with its own bits. The server writes and clients read; a client may
 * write a bit only to predict a change it has also asked the server for.
 */
public final class StatusWord implements VehicleComponent {

    public static final int FLARE = 0;
    public static final int FREE_LOOK = 1;
    public static final int RELOADING = 2;
    public static final int INFINITE_AMMO = 3;
    public static final int INFINITE_FUEL = 4;
    public static final int REPELLING = 5;
    public static final int SEARCH_LIGHT = 6;
    public static final int MOVE_LEFT = 7;
    public static final int MOVE_RIGHT = 8;
    public static final int THROTTLE_UP = 9;
    public static final int THROTTLE_DOWN = 10;
    public static final int BRAKE = 11;
    public static final int GUNNER = 12;
    public static final int RADAR = 13;
    public static final int ECM_JAMMER = 15;

    private final SynchedEntityData data;
    private final EntityDataAccessor<Integer> key;
    private final boolean clientSide;
    private int bits;

    public StatusWord(SynchedEntityData data, EntityDataAccessor<Integer> key, boolean clientSide) {
        this.data = data;
        this.key = key;
        this.clientSide = clientSide;
    }

    public boolean get(int bit) {
        return (this.bits >> bit & 1) != 0;
    }

    /** Server only; a client call is ignored. */
    public void set(int bit, boolean value) {
        this.set(bit, value, false);
    }

    /** Sets a bit; {@code predictOnClient} lets a client write it locally too. */
    public void set(int bit, boolean value, boolean predictOnClient) {
        if (this.clientSide && !predictOnClient) {
            return;
        }
        int previous = this.bits;
        if (value) {
            this.bits |= 1 << bit;
        } else {
            this.bits &= ~(1 << bit);
        }
        if (previous != this.bits) {
            this.data.set(this.key, this.bits);
        }
    }

    /** Client: takes over the word the server synced. */
    public void readSynced() {
        this.bits = this.data.get(this.key);
    }

    @Override
    public void describe(StateSink state) {
        String[] names = {"flare", "free look", "reloading", "infinite ammo", "infinite fuel", "repelling",
                "search light", "move left", "move right", "throttle up", "throttle down", "brake", "gunner",
                "radar", "", "ecm jammer"};
        StringBuilder set = new StringBuilder();
        for (int bit = 0; bit < names.length; bit++) {
            if (get(bit) && !names[bit].isEmpty()) {
                set.append(set.isEmpty() ? "" : ", ").append(names[bit]);
            }
        }
        state.put("bits", "0x" + Integer.toHexString(this.bits));
        state.put("set", set.isEmpty() ? "-" : set.toString());
    }
}
