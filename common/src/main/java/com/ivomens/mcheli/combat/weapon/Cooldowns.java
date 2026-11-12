package com.ivomens.mcheli.combat.weapon;

/** Cooldown rules shared by every weapon. */
public final class Cooldowns {

    /** A padded cooldown this long or longer is stored negative (see {@link #padded}). */
    private static final int LONG_COOLDOWN = 40;

    private Cooldowns() {
    }

    /**
     * The cooldown of a weapon padded for the client and for shooters that are not players, so neither
     * fires before the server is ready. Long ones become negative: the weapon group then counts the
     * cooldown up to zero and reports itself busy meanwhile.
     */
    public static int padded(int delay) {
        if (delay < 4) return delay + 1;
        if (delay < 7) return delay + 2;
        if (delay < 10) return delay + 3;
        if (delay < 20) return delay + 6;
        int padded = delay + 10;
        return padded >= LONG_COOLDOWN ? -padded : padded;
    }
}
