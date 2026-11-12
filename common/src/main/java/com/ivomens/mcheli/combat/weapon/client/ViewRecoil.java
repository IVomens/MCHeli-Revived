package com.ivomens.mcheli.combat.weapon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Weapon recoil applied to the local player's view. A shot kicks the view; the kick decays by the weapon's
 * recoil control every tick and a fraction of it is pulled back, so the view drifts most of the way home.
 *
 * <p>One per client, owned by the client session; only the client thread touches it.
 */
public final class ViewRecoil {

    private static final float RECOVERY_RATE = 0.2F;
    private static final float DECAY = 0.8F;

    private float recoilPitch;
    private float recoilYaw;
    private float recoveryPitch;
    private float recoveryYaw;
    private float recoilControl = 0.8f;

    /** A shot kicks the view up by {@code pitch} and sideways by {@code yaw} degrees. */
    public void kick(float pitch, float yaw, float control) {
        this.recoilPitch += pitch;
        this.recoilYaw += yaw;
        this.recoilControl = control;
    }

    /** Once per client tick: applies what is left of the kick and pulls part of it back. */
    public void onUpdate() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (this.recoilPitch > 0) {
            this.recoilPitch *= this.recoilControl;
        }
        this.recoveryPitch += this.recoilPitch;
        this.recoveryYaw += this.recoilYaw;
        player.setXRot(player.getXRot() - this.recoilPitch + this.recoveryPitch * RECOVERY_RATE);
        player.setYRot(player.getYRot() - this.recoilYaw + this.recoveryYaw * RECOVERY_RATE);

        this.recoveryPitch *= DECAY;
        this.recoveryYaw *= DECAY;
        this.recoilYaw *= DECAY;
    }
}
