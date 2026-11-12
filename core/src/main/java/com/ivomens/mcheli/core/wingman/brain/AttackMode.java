package com.ivomens.mcheli.core.wingman.brain;

/** Whether and how a wingman attacks. The codes are what the wingman panel sends and receives. */
public enum AttackMode {
    /** Holding fire. */
    NONE(0),
    /** Attacking the target its leader picked, or the leader's lock. */
    MANUAL(1),
    /** Picking the nearest hostile itself. */
    AUTO(2);

    private final int code;

    AttackMode(int code) {
        this.code = code;
    }

    public int code() {
        return this.code;
    }

    public static AttackMode ofCode(int code) {
        for (AttackMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        return NONE;
    }

    public boolean isAttacking() {
        return this != NONE;
    }
}
