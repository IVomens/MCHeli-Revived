package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.mission.MissionOrder;

/** A mission order being flown and what has happened on it so far. */
public final class OrderProgress {

    private final MissionOrder order;
    /** Ticks on station. */
    int timer;
    int strikePassesRemaining;
    /** Hostile mobs counted during recon. */
    int reconMobCount;
    /** Why the aircraft is returning to base, for status lines. */
    String rtbReason = "";
    /** Ticks since the order was issued; spaces out repeated warnings. */
    int diagnosticTick;

    OrderProgress(MissionOrder order) {
        this.order = order;
        this.strikePassesRemaining = order.strikePasses();
    }

    public MissionOrder order() {
        return this.order;
    }

    public int timer() {
        return this.timer;
    }

    public String rtbReason() {
        return this.rtbReason;
    }
}
