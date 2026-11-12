package com.ivomens.mcheli.equipment.launcher.client;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.client.input.InputKey;
import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.config.KeySettings;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.equipment.gltd.GltdEntity;
import com.ivomens.mcheli.equipment.launcher.LightWeaponItem;
import com.ivomens.mcheli.equipment.launcher.net.LauncherFireMessage;
import com.ivomens.mcheli.equipment.launcher.net.LauncherReloadMessage;
import com.ivomens.mcheli.equipment.launcher.net.NightVisionMessage;
import com.ivomens.mcheli.client.input.ClientTickHandler;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.sound.client.UiSounds;
import com.ivomens.mcheli.util.Inventories;
import com.ivomens.mcheli.vehicle.client.camera.VehicleCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the handheld launchers: aiming zoom, lock-on, reloading, night vision and firing.
 * The launcher's state lives in the {@link HandheldWeaponSession} the client shell owns.
 */
public final class LauncherClientTickHandler extends ClientTickHandler {

    private static final int RELOAD_TICKS = 40;
    private static final int RELOAD_SOUND_TICK = 10;
    /** Night vision can only be switched on after aiming this long. */
    private static final int NIGHT_VISION_AIM_TICKS = 60;
    /** Night vision with less time left than this was ours and is removed when the launcher is lowered. */
    private static final int OWN_NIGHT_VISION_TICKS = 250;
    /** Top attack (mode 1+) needs the target at least this far away horizontally. */
    private static final double MIN_TOP_ATTACK_DIST = 40.0;

    private final HandheldWeaponSession session;
    private final VehicleCamera camera;
    private InputKey keyAttack;
    private InputKey keySwitchWeaponMode;
    private InputKey keyZoom;
    private InputKey keyCameraMode;
    private InputKey[] keys;
    private boolean isHeldItem;
    private Player prevPlayer;
    private ItemStack prevItemStack = ItemStack.EMPTY;

    public LauncherClientTickHandler(Minecraft minecraft, KeySettings bindings, HandheldWeaponSession session,
                                     VehicleCamera camera) {
        super(minecraft);
        this.camera = camera;
        this.session = session;
        this.updateKeybind(bindings);
    }

    /** Night vision the player has, in ticks left; 0 for none. */
    public static int nightVisionTicks(Player player) {
        MobEffectInstance effect = player.getEffect(MobEffects.NIGHT_VISION);
        return effect != null ? effect.getDuration() : 0;
    }

    @Override
    public void updateKeybind(KeySettings bindings) {
        this.keyAttack = new InputKey(KeySettings.ATTACK);
        this.keySwitchWeaponMode = new InputKey(bindings.switchWeaponMode());
        this.keyZoom = new InputKey(bindings.zoom());
        this.keyCameraMode = new InputKey(bindings.cameraMode());
        this.keys = new InputKey[]{this.keyAttack, new InputKey(KeySettings.USE_WEAPON), this.keySwitchWeaponMode,
                this.keyZoom, this.keyCameraMode};
    }

    @Override
    public void onTick(boolean inGUI) {
        for (InputKey key : this.keys) {
            key.update();
        }
        boolean wasHeld = this.isHeldItem;
        Player player = this.mc.player;
        if (this.prevPlayer != player) {
            this.session.reset();
            this.prevPlayer = player;
        }
        // launchers can't be used from a vehicle or the laser designator
        ItemStack stack = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        if (player == null || player.getVehicle() instanceof GltdEntity || player.getVehicle() instanceof AircraftEntity) {
            stack = ItemStack.EMPTY;
        }
        this.session.forgetLostMarker();
        if (!stack.isEmpty() && stack.getItem() instanceof LightWeaponItem launcher) {
            if (this.isOtherItem(stack)) {
                this.session.hold(player, LightWeaponItem.getLightWeaponName(stack));
            }
            if (this.session.weapon() == null) {
                return;
            }
            this.tickHeldLauncher(player, stack, launcher, inGUI);
        } else {
            this.session.lockToneCountdown = 0;
            this.session.reloadTicks = 0;
            this.isHeldItem = false;
        }
        if (wasHeld && !this.isHeldItem) {
            this.onLauncherLowered(player);
        }
        this.prevItemStack = stack;
        this.session.releaseUnheldLock();
    }

    private boolean isOtherItem(ItemStack stack) {
        return this.prevItemStack.isEmpty() || !ItemStack.isSameItem(this.prevItemStack, stack)
                && !this.prevItemStack.getDescriptionId().equals(stack.getDescriptionId());
    }

    private void tickHeldLauncher(Player player, ItemStack stack, LightWeaponItem launcher, boolean inGUI) {
        this.session.useLockRange(McheliConfig.server().stingerLockRange());
        LockOnSeeker seeker = this.session.seeker();
        WeaponMount weapon = this.session.weapon();
        boolean aiming = player.getTicksUsingItem() > LightWeaponItem.AIM_TICKS;
        if (aiming) {
            this.session.clampZoom(weapon.spec().camera().zoom().size());
            this.camera.setZoom(weapon.spec().camera().zoom().get(this.session.zoom()));
        } else {
            this.camera.setZoom(1.0F);
        }
        if (isLoaded(stack)) {
            if (aiming) {
                seeker.lock(player);
                this.playLockOnSound(seeker);
            } else {
                this.camera.setZoom(1.0F);
                seeker.clearLock();
            }
            this.session.reloadTicks = 0;
        } else {
            this.session.lockToneCountdown = 0;
            this.tickReload(player, launcher);
            seeker.clearLock();
        }
        if (!inGUI) {
            this.playerControl(player, stack, weapon, seeker);
        }
        this.isHeldItem = LightWeaponItem.isHeld(player);
    }

    /** The launcher's damage counts the fired shots; at full damage it is empty. */
    private static boolean isLoaded(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage();
    }

    /** The lock-on tone beeps faster as the lock builds up. */
    private void playLockOnSound(LockOnSeeker seeker) {
        if (seeker.lockCount() <= 0) {
            return;
        }
        if (this.session.lockToneCountdown > 0) {
            this.session.lockToneCountdown--;
            return;
        }
        int countdown = (int) (7 * (1.0 - (float) seeker.lockCount() / seeker.lockTicks()));
        this.session.lockToneCountdown = Math.max(countdown, 2);
        UiSounds.play("lockon", 1.0F, 1.0F);
    }

    private void tickReload(Player player, LightWeaponItem launcher) {
        if (!Inventories.hasItem(player, launcher.bullet) || player.getUseItemRemainingTicks() > 0) {
            this.session.reloadTicks = 0;
            return;
        }
        if (this.session.reloadTicks == RELOAD_SOUND_TICK) {
            UiSounds.play("fim92_reload", 1.0F, 1.0F);
        }
        if (this.session.reloadTicks < RELOAD_TICKS && ++this.session.reloadTicks == RELOAD_TICKS) {
            Network.sendToServer(new LauncherReloadMessage());
        }
    }

    private void onLauncherLowered(Player player) {
        if (nightVisionTicks(player) < OWN_NIGHT_VISION_TICKS) {
            Network.sendToServer(new NightVisionMessage(false));
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        this.camera.setZoom(1.0F);
    }

    private void playerControl(Player player, ItemStack stack, WeaponMount weapon, LockOnSeeker seeker) {
        if (this.keySwitchWeaponMode.isKeyDown() && weapon.stats().modes() > 1) {
            this.session.nextMode(weapon.stats().modes());
            UiSounds.play("pi", 0.5F, 0.9F);
        }
        LauncherFireMessage fire = this.pullTrigger(player, stack, seeker);
        if (this.keyZoom.isKeyDown() && this.session.nextZoom(weapon.spec().camera().zoom().size())) {
            playSound("zoom", 0.5F, 1.0F);
        }
        // the server switches night vision before it fires, as the sight did before the shot
        if (this.keyCameraMode.isKeyDown()) {
            this.toggleNightVision(player);
        }
        if (fire != null) {
            Network.sendToServer(fire);
        }
    }

    /** The shot to send for a completed lock, or null; with auto fire the lock pulls the trigger itself. */
    private LauncherFireMessage pullTrigger(Player player, ItemStack stack, LockOnSeeker seeker) {
        boolean autoFire = McheliConfig.controls().lightWeaponAutoFire() && isLoaded(stack) && seeker.isLockComplete();
        if (!this.keyAttack.isKeyPress() && !autoFire) {
            return null;
        }
        if (isLoaded(stack) && seeker.isLockComplete() && this.isTargetInModeRange(player, seeker)) {
            int targetId = seeker.lastLocked() != null ? seeker.lastLocked().getId() : -1;
            Vec3 eyes = new Vec3(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
            seeker.clearLock();
            return new LauncherFireMessage(targetId, this.session.mode(), eyes);
        }
        if (this.keyAttack.isKeyDown() && player.getTicksUsingItem() > 5) {
            playSoundNG();
        }
        return null;
    }

    private boolean isTargetInModeRange(Player player, LockOnSeeker seeker) {
        Entity target = seeker.target();
        if (this.session.mode() <= 0 || target == null) {
            return true;
        }
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        return Math.sqrt(dx * dx + dz * dz) >= MIN_TOP_ATTACK_DIST;
    }

    private void toggleNightVision(Player player) {
        if (player.getEffect(MobEffects.NIGHT_VISION) != null) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            Network.sendToServer(new NightVisionMessage(false));
        } else if (player.getTicksUsingItem() > NIGHT_VISION_AIM_TICKS) {
            Network.sendToServer(new NightVisionMessage(true));
        } else {
            playSoundNG();
            return;
        }
        UiSounds.play("pi", 0.5F, 0.9F);
    }
}
