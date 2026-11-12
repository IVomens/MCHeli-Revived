package com.ivomens.mcheli.equipment.rangefinder.client;

import com.ivomens.mcheli.equipment.rangefinder.RangeFinderItem;
import com.ivomens.mcheli.equipment.rangefinder.net.SpotRequestMessage;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.sound.client.UiSounds;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The local player's range finder scope: whether it is raised, its zoom, its spotting mode and the
 * cooldown between two spots. Raising the scope zooms the camera; lowering it resets the zoom.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class RangeFinderScope {

    public static final int MODE_SPOT_PLAYERS = 0;
    public static final int MODE_SPOT_MOBS = 1;
    public static final int MODE_MARK_POINT = 2;
    public static final int MODE_COUNT = 3;
    public static final float MIN_ZOOM = 1.2F;
    public static final float MAX_ZOOM = 10.0F;

    private static final float START_ZOOM = 2.0F;
    private static final int SPOT_COOLDOWN_TICKS = 60;
    /** Entity filters the server spots with (see the spot request handler). */
    private static final int FILTER_PLAYERS_AND_VEHICLES = 60;
    private static final int FILTER_MOBS = 3;

    private final VehicleCamera camera;
    private int spotCooldown;
    private boolean raised;
    private float zoom = START_ZOOM;
    private int mode = MODE_SPOT_PLAYERS;

    public RangeFinderScope(VehicleCamera camera) {
        this.camera = camera;
    }

    /** Whether the player looks through the scope: aimed long enough, or raised and not yet lowered. */
    public boolean isUsingScope(Player player) {
        return player.getTicksUsingItem() > RangeFinderItem.SCOPE_DELAY_TICKS || this.raised;
    }

    public boolean isRaised() {
        return this.raised;
    }

    /** Raises the scope at its starting zoom. */
    public void raise() {
        this.zoom = START_ZOOM;
        this.camera.setZoom(START_ZOOM);
        this.raised = true;
    }

    /** Lowers the scope; the camera zoom goes back to normal. */
    public void lower() {
        this.camera.setZoom(1.0F);
        this.raised = false;
    }

    public float zoom() {
        return this.zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
        this.camera.setZoom(zoom);
    }

    public int mode() {
        return this.mode;
    }

    /** Switches to the next mode; there is no team to spot players for in single player. */
    public void nextMode(boolean singleplayer) {
        this.mode = (this.mode + 1) % MODE_COUNT;
        if (singleplayer && this.mode == MODE_SPOT_PLAYERS) {
            this.mode = MODE_SPOT_MOBS;
        }
    }

    /** Joining a world starts spotting mobs in single player and players otherwise. */
    public void resetMode(boolean singleplayerServer) {
        this.mode = singleplayerServer ? MODE_SPOT_MOBS : MODE_SPOT_PLAYERS;
    }

    /** Ticks left until the next spot is allowed. */
    public int spotCooldown() {
        return this.spotCooldown;
    }

    /** Once per client tick. */
    public void tickCooldown() {
        if (this.spotCooldown > 0) {
            this.spotCooldown--;
        }
    }

    /** Asks the server to spot or mark in the current mode, once the scope is up and the cooldown over. */
    public void spot(Player player, ItemStack rangeFinder) {
        if (player == null || !player.level().isClientSide || this.spotCooldown != 0
                || player.getTicksUsingItem() <= RangeFinderItem.SCOPE_DELAY_TICKS) {
            return;
        }
        if (this.mode == MODE_MARK_POINT) {
            this.spotCooldown = SPOT_COOLDOWN_TICKS;
            Network.sendToServer(new SpotRequestMessage(0));
        } else if (rangeFinder.getDamageValue() < rangeFinder.getMaxDamage()) {
            this.spotCooldown = SPOT_COOLDOWN_TICKS;
            int filter = this.mode == MODE_SPOT_PLAYERS ? FILTER_PLAYERS_AND_VEHICLES : FILTER_MOBS;
            Network.sendToServer(new SpotRequestMessage(filter));
        } else {
            UiSounds.play("ng", 1.0F, 1.0F);
        }
    }
}
