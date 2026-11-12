package com.ivomens.mcheli.vehicle.client.input;

import com.ivomens.mcheli.core.sim.math.LegacyMath;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * The pitch range a seat holds the local player to during one client tick. The tick loop clears it at the
 * start of every tick, a vehicle handler sets it while the player sits in a limited seat, and it is applied
 * to the player after every frame, so mouse look between ticks cannot leave the range.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class PitchLimit {

    private float minPitch = -90.0F;
    private float maxPitch = 90.0F;
    private boolean limited;

    /** Limits the pitch for this tick and pulls the player into the range right away. */
    public void limit(float min, float max, @Nullable Entity player) {
        this.minPitch = min;
        this.maxPitch = max;
        this.limited = true;
        if (player != null) {
            player.setXRot(LegacyMath.limit(player.getXRot(), this.minPitch, this.maxPitch));
        }
    }

    /** Lifts the limit; called at the start of every client tick. */
    public void clear() {
        this.minPitch = -90.0F;
        this.maxPitch = 90.0F;
        this.limited = false;
    }

    /** Pulls the entity's pitch, and its previous pitch, into the range if one is set. */
    public void apply(@Nullable Entity entity) {
        if (entity == null || !this.limited) return;
        if (entity.getXRot() < this.minPitch) {
            entity.setXRot(this.minPitch);
            entity.xRotO = this.minPitch;
        } else if (entity.getXRot() > this.maxPitch) {
            entity.setXRot(this.maxPitch);
            entity.xRotO = this.maxPitch;
        }
    }
}
