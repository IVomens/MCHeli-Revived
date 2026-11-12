package com.ivomens.mcheli.api.target;

/**
 * Where an addon's target counts as being, for seekers that lock only air or only ground targets (an
 * air-to-air missile, an anti-tank missile).
 *
 * <p>Immutable; any thread, either side. Since API 1.1. A minor version may add constants, so a {@code switch}
 * over this enum needs a {@code default} branch.
 */
public enum TargetDomain {
    /** Always an air target, wherever it is. */
    AIR,
    /** Always a ground target, wherever it is. */
    GROUND,
    /**
     * Decided by height, as for MCHeli's own vehicles: a ground target while it stands on something or has a
     * solid block within the seeker's minimum lock height below it, an air target otherwise.
     */
    BY_HEIGHT
}
