package com.ivomens.mcheli.combat.countermeasure;

import javax.annotation.Nullable;

/**
 * How a dispenser releases its flares. Vehicle files name a pattern by its legacy number, 1 to 10;
 * the number used to index the parameter tables directly, which is why 6 to 9 exist: they are the
 * standard salvo with no sideways throw ({@link #TRAILING}).
 *
 * @param salvoSize  flares per salvo
 * @param interval   ticks between salvos
 * @param cooldown   ticks from release until the dispenser is ready again, on the server
 * @param decoyTicks ticks at the start of the cooldown during which the flares decoy missiles
 * @param maxSalvos  salvos per use
 */
public enum FlarePattern {

    /** Single flares thrown in a random direction. */
    RANDOM(1, 3, 200, 100, 16),
    /** Three flares to the left, right and back. */
    SIDEWAYS(3, 5, 300, 200, 16),
    /** Left and right only, faster. */
    SIDEWAYS_PAIRS(2, 3, 200, 100, 16),
    /** Single flares thrown far forwards, falling slowly. */
    FORWARD_WIDE(1, 3, 200, 100, 16),
    /** Pairs thrown a little forwards. */
    FORWARD_NARROW(2, 3, 200, 100, 16),
    /** The standard salvo, dropped with the vehicle's own motion (legacy numbers 6 to 9). */
    TRAILING(1, 3, 200, 100, 16),
    /** One ring of eight flares around the vehicle, popping after a short fuse. */
    RING(8, 1, 250, 60, 1);

    /** The client waits this much longer before it shows the dispenser as ready again. */
    public static final int CLIENT_EXTRA_COOLDOWN = 50;

    private final int salvoSize;
    private final int interval;
    private final int cooldown;
    private final int decoyTicks;
    private final int maxSalvos;

    FlarePattern(int salvoSize, int interval, int cooldown, int decoyTicks, int maxSalvos) {
        this.salvoSize = salvoSize;
        this.interval = interval;
        this.cooldown = cooldown;
        this.decoyTicks = decoyTicks;
        this.maxSalvos = maxSalvos;
    }

    /** The pattern of a legacy flare number, or null for numbers outside 1 to 10. */
    @Nullable
    public static FlarePattern ofLegacyNumber(int number) {
        return switch (number) {
            case 1 -> RANDOM;
            case 2 -> SIDEWAYS;
            case 3 -> SIDEWAYS_PAIRS;
            case 4 -> FORWARD_WIDE;
            case 5 -> FORWARD_NARROW;
            case 6, 7, 8, 9 -> TRAILING;
            case 10 -> RING;
            default -> null;
        };
    }

    public int salvoSize() {
        return this.salvoSize;
    }

    public int interval() {
        return this.interval;
    }

    public int cooldown(boolean client) {
        return client ? this.cooldown + CLIENT_EXTRA_COOLDOWN : this.cooldown;
    }

    /** Whether flares released this way still decoy missiles with {@code ticksLeft} of the cooldown to go. */
    public boolean isDecoying(int ticksLeft, boolean client) {
        return ticksLeft != 0 && ticksLeft > this.cooldown(client) - this.decoyTicks;
    }

    public int maxSalvos() {
        return this.maxSalvos;
    }
}
