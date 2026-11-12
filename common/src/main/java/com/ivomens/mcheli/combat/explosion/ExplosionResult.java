package com.ivomens.mcheli.combat.explosion;

/**
 * What an explosion did that its cause cares about.
 *
 * @param hitEntity it hurt at least one entity that counts as a hit for the hit marker
 */
public record ExplosionResult(boolean hitEntity) {

    /** Explosions on the client do nothing but draw. */
    public static final ExplosionResult NONE = new ExplosionResult(false);
}
