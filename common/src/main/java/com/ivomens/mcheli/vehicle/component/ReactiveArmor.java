package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.combat.explosion.Explosions;
import com.ivomens.mcheli.combat.explosion.ExplosionSpec;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Explosive reactive armor (ERA): part boxes flagged as ERA tiles pop when hit hard enough and may
 * detonate. The intact/popped state of every tile is saved and synced as a compact '1'/'0' string in
 * box order.
 */
public final class ReactiveArmor {

    private final PartBoxesComponent parts;
    private final SyncedValue<String> syncedValue;
    /** Last state string written to or read from the synced data; null forces a resync. */
    @Nullable
    private String syncedState;
    /** Set whenever a tile changes on the server, so the state string is only rebuilt then. */
    private boolean dirty = true;

    ReactiveArmor(PartBoxesComponent parts, SyncedValue<String> syncedValue) {
        this.parts = parts;
        this.syncedValue = syncedValue;
    }

    /** Called when the part boxes are recreated from a new aircraft info. */
    void onBoxesReplaced() {
        this.syncedState = null;
        this.dirty = true;
    }

    /**
     * Server: pops the tile if the hit was strong enough. Returns quietly for non-ERA boxes and tiles
     * that are already gone.
     */
    public void onHit(Level level, @Nullable PartBoundingBox box, float damage, @Nullable Entity attacker) {
        if (box == null || !box.isERA() || !box.eraActive() || damage <= box.eraMinDamage()) {
            return;
        }
        box.setEraActive(false);
        this.dirty = true;
        syncToClients(false);
        if (box.eraExplosion() > 0.0F && box.center() != null) {
            Entity exploder = attacker instanceof Player ? attacker : null;
            Explosions.detonate(level, ExplosionSpec.at(box.center(), box.eraExplosion()).blockSize(0.0F).build(), null,
                    exploder);
        }
    }

    /** Server, per tick: pushes changes. */
    public void serverTick() {
        if (this.dirty) {
            syncToClients(false);
        }
    }

    /** Client, per tick: applies what the server sent. */
    public void clientTick() {
        String received = this.syncedValue.get();
        if (received != null && !received.equals(this.syncedState)) {
            this.syncedState = received;
            applyState(received);
        }
    }

    /** Server: writes the state string to the synced data if it changed (or always when forced). */
    void syncToClients(boolean force) {
        String state = buildState();
        this.dirty = false;
        if (force || !state.equals(this.syncedState)) {
            this.syncedState = state;
            this.syncedValue.set(state);
        }
    }

    String buildState() {
        StringBuilder state = new StringBuilder();
        for (PartBoundingBox box : this.parts.boxes()) {
            if (box.isERA()) {
                state.append(box.eraActive() ? '1' : '0');
            }
        }
        return state.toString();
    }

    void applyState(@Nullable String state) {
        if (state == null) {
            return;
        }
        int tile = 0;
        for (PartBoundingBox box : this.parts.boxes()) {
            if (!box.isERA()) {
                continue;
            }
            if (tile < state.length()) {
                box.setEraActive(state.charAt(tile) != '0');
            }
            tile++;
        }
        this.dirty = true;
    }
}
