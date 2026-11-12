package com.ivomens.mcheli.combat.explosion;

/**
 * How cover between an explosion and an entity reduces the damage.
 *
 * @param throughWalls  the blast ignores how much of the entity it can see (full exposure)
 * @param behindCover   with {@code throughWalls}, the share of the damage an entity behind cover still takes
 */
public record BlastCover(boolean throughWalls, float behindCover) {

    /** The usual rule: damage scales with the share of the entity the blast can see. */
    public static final BlastCover EXPOSURE = new BlastCover(false, 1.0F);
}
