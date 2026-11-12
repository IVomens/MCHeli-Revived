package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.entityview.AiGunner;
import com.ivomens.mcheli.combat.CombatRandom;
import com.ivomens.mcheli.platform.LocalClient;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Identical weapons fired in turn (the two pods of a rocket pair, say) with their shared magazine and
 * reserve, heat, cooldown and reload, barrel spin, turret angles and recoil.
 * <p>
 * A vehicle has one group per weapon its definition lists; a handheld weapon is a group of one.
 */
public final class WeaponGroup {

    private static final int MODE_SWITCH_COOLDOWN = 15;
    private static final int WEAPON_SWITCH_COOLDOWN_CLIENT = 25;
    private static final int WEAPON_SWITCH_COOLDOWN_SERVER = 15;
    /** A shot that lasts this many ticks of the last-used count shows as still firing. */
    private static final int SHOWN_FIRING_TICKS = 4;
    /** Overheating adds this much heat on top, so the weapon stays silent a while. */
    private static final int OVERHEAT_PENALTY = 30;
    /** The server finishes a reload this much earlier than the client shows it. */
    private static final int SERVER_RELOAD_HEAD_START = 20;
    private static final int COOLDOWN_SPANS_RELOAD_MARGIN = 10;

    private final Level level;
    private final List<WeaponMount> mounts;
    private final int[] lastUsedCount;
    private final RecoilBuffer[] recoil;
    private final BarrelSpin barrels = new BarrelSpin();
    private final TurretAngles turret = new TurretAngles();
    private int ammo;
    private int reserve;
    private int heat;
    private int heatRecovery = 1;
    private int cooldown;
    private int reloadTicks;
    private int soundWait;
    private int currentIndex;
    /** What the last shot asked the server for; the client sends it with the trigger pull. */
    private FireRequest lastRequest = FireRequest.NONE;

    public WeaponGroup(Level level, List<WeaponMount> mounts) {
        this.level = level;
        this.mounts = List.copyOf(mounts);
        this.lastUsedCount = new int[mounts.size()];
        this.recoil = new RecoilBuffer[mounts.size()];
        for (int i = 0; i < mounts.size(); i++) {
            WeaponSpec info = mounts.get(i).spec();
            this.recoil[i] = new RecoilBuffer(info.recoil().bufferCount(), info.recoil().bufferSpeed());
        }
        this.setReserveAmmo(0);
    }

    /** A group of one, as handheld weapons are. */
    public static WeaponGroup of(Level level, WeaponMount mount) {
        return new WeaponGroup(level, List.of(mount));
    }

    /** The group of an empty weapon slot: one placeholder that never fires. */
    public static WeaponGroup placeholder(Level level) {
        return new WeaponGroup(level, List.of(WeaponMount.placeholder(level)));
    }

    // Identity

    public WeaponSpec spec() {
        return this.firstMount().spec();
    }

    public WeaponKind kind() {
        return this.firstMount().kind();
    }

    public boolean isNamed(String name) {
        return this.firstMount().name().equalsIgnoreCase(name);
    }

    public String displayName() {
        WeaponMount current = this.currentMount();
        return current != null ? current.displayName() : "";
    }

    public WeaponMount firstMount() {
        return this.mounts.getFirst();
    }

    @Nullable
    public WeaponMount currentMount() {
        return this.mount(this.currentIndex);
    }

    @Nullable
    public WeaponMount mount(int index) {
        return index >= 0 && index < this.mounts.size() ? this.mounts.get(index) : null;
    }

    public int mountCount() {
        return this.mounts.size();
    }

    public int currentIndex() {
        return this.currentIndex;
    }

    public FireRequest lastRequest() {
        return this.lastRequest;
    }

    // Ammunition

    public int magazineSize() {
        return this.spec().round();
    }

    public int maxAmmo() {
        return this.spec().ammo().maxAmmo();
    }

    public int ammo() {
        return this.ammo;
    }

    public void setAmmo(int ammo) {
        this.ammo = ammo;
    }

    public int reserveAmmo() {
        return this.reserve;
    }

    /** Sets the reserve, capped so magazine plus reserve never exceed the weapon's max ammo. */
    public void setReserveAmmo(int amount) {
        this.reserve = Math.min(amount, this.spec().ammo().maxAmmo() - this.ammo);
    }

    /** Adds the definition's resupply amount, up to the max ammo. */
    public void resupply() {
        WeaponSpec info = this.spec();
        if (this.reserve + this.ammo < info.ammo().maxAmmo()) {
            this.setReserveAmmo(this.reserve + this.ammo + info.ammo().resupplyCount());
        }
    }

    public int heat() {
        return this.heat;
    }

    public int cooldown() {
        return this.cooldown;
    }

    public int reloadTicks() {
        return this.reloadTicks;
    }

    /** Takes over the cooldown and reload the server reports, which may differ from the client's prediction. */
    public void syncTimers(int cooldownTicks, int reload) {
        this.cooldown = cooldownTicks;
        this.reloadTicks = reload;
    }

    public boolean isBusy() {
        return this.cooldown < 0 || this.reloadTicks > 0;
    }

    public boolean canFire() {
        return this.cooldown == 0 && this.reloadTicks == 0;
    }

    public boolean hasLongDelay() {
        return this.spec().delay() > 4;
    }

    /** Empties the magazine into the reserve and starts a reload; false while busy or when it cannot help. */
    public boolean manualReload() {
        int inMagazine = this.ammo;
        int inReserve = this.reserve;
        if (this.isBusy() || inMagazine >= this.magazineSize() || inReserve <= 0) {
            return false;
        }
        this.ammo = 0;
        this.setReserveAmmo(inReserve + inMagazine);
        this.reload();
        if (this.level.isClientSide) {
            LocalClient.get().playClick();
        }
        return true;
    }

    /** Starts the reload of a magazine that is not full; the client shows it emptied meanwhile. */
    public void reload() {
        WeaponSpec info = this.spec();
        if (this.magazineSize() > 0 && this.ammo < this.magazineSize() && info.ammo().reloadTime() > 0) {
            this.reloadTicks = info.ammo().reloadTime();
            if (this.level.isClientSide) {
                this.ammo = 0;
            } else {
                this.reloadTicks = Math.max(1, this.reloadTicks - SERVER_RELOAD_HEAD_START);
            }
        }
    }

    /** Fills the magazine from the reserve. */
    public void reloadMagazine() {
        int needed = this.magazineSize() - this.ammo;
        if (needed > 0) {
            int transfer = Math.min(needed, this.reserve);
            this.ammo += transfer;
            this.setReserveAmmo(this.reserve - transfer);
        }
    }

    // Modes and selection

    public void switchMode() {
        boolean changed = false;
        for (WeaponMount mount : this.mounts) {
            changed |= mount.switchMode();
        }
        if (changed) {
            this.startSwitchCooldown(MODE_SWITCH_COOLDOWN);
            if (this.level.isClientSide) {
                LocalClient.get().playClick();
            }
        }
    }

    /** The group was selected: a short wait, and a creative crew gets a full magazine. */
    public void onSelected(boolean creative) {
        this.startSwitchCooldown(this.level.isClientSide ? WEAPON_SWITCH_COOLDOWN_CLIENT : WEAPON_SWITCH_COOLDOWN_SERVER);
        this.currentIndex = 0;
        if (creative) {
            this.ammo = this.magazineSize();
        }
    }

    private void startSwitchCooldown(int ticks) {
        if (this.cooldown >= -ticks) {
            this.cooldown = this.cooldown > ticks ? -this.cooldown : -ticks;
        }
    }

    /** Another group of the same weapon group fired: this one waits too, and reloads with it. */
    public void followGroupShot(boolean reload) {
        WeaponMount current = this.currentMount();
        if (current == null) {
            return;
        }
        this.cooldown = current.stats().interval();
        this.reloadTicks = 0;
        if (reload && this.magazineSize() > 0 && current.spec().ammo().reloadTime() > 0) {
            int headStart = this.level.isClientSide ? 0 : SERVER_RELOAD_HEAD_START;
            this.reloadTicks = Math.max(1, current.spec().ammo().reloadTime() - headStart);
        }
    }

    // Firing

    /**
     * Fires the current mount from {@code base} plus its muzzle offset, if there is ammo and headroom.
     * The server uses this; it does not check the cooldown, the shooter's side already did.
     */
    public FireOutcome fire(Shooter shooter, Vec3 base, FireRequest request, boolean infiniteAmmo) {
        WeaponMount current = this.currentMount();
        if (!this.hasAmmoAndHeadroom(current)) return FireOutcome.NOT_FIRED;
        Shot shot = this.aim(shooter, current, base.add(shooter.muzzleOffset(current)), request);
        if (!shot.allowedByListeners() || !current.fireMode().fire(shot)) return FireOutcome.NOT_FIRED;
        current.onShot();
        return this.afterShot(shooter, current, infiniteAmmo);
    }

    /** Client: predicts the shot of the current mount from {@code base}, if it is ready, and records what to send. */
    public FireOutcome predictFire(Shooter shooter, Vec3 base, boolean infiniteAmmo) {
        WeaponMount current = this.currentMount();
        if (!this.hasAmmoAndHeadroom(current) || !this.canFire()) return FireOutcome.NOT_FIRED;
        Shot shot = this.aim(shooter, current, base, current.request());
        if (!current.fireMode().predict(shot)) return FireOutcome.NOT_FIRED;
        this.lastRequest = current.request();
        return this.afterShot(shooter, current, infiniteAmmo);
    }

    private boolean hasAmmoAndHeadroom(@Nullable WeaponMount current) {
        if (current == null) return false;
        WeaponSpec info = current.spec();
        boolean hasAmmo = this.magazineSize() <= 0 || this.ammo > 0;
        boolean notOverheated = info.missile().heat().maxHeatCount() <= 0
                || this.heat < info.missile().heat().maxHeatCount();
        return hasAmmo && notOverheated;
    }

    /** The shot along the barrels with the weapon's spread; shots launch level, ignoring the platform's roll. */
    private Shot aim(Shooter shooter, WeaponMount mount, Vec3 origin, FireRequest request) {
        Vec2 aim = shooter.barrelAim(this, mount);
        Vec2 spread = Shots.scatter(CombatRandom.of(this.level).aim(), aim.x, aim.y,
                mount.spec().definition().accuracy());
        return new Shot(this.level, shooter, mount, origin, Mth.wrapDegrees(spread.x), Mth.wrapDegrees(spread.y), 0.0F,
                request, this.soundWait == 0);
    }

    private FireOutcome afterShot(Shooter shooter, WeaponMount current, boolean infiniteAmmo) {
        WeaponSpec info = current.spec();
        if (info.missile().heat().maxHeatCount() > 0) {
            this.heatRecovery = 1;
            this.heat += current.stats().heatPerShot();
            if (this.heat >= info.missile().heat().maxHeatCount()) this.heat += OVERHEAT_PENALTY;
        }
        if (info.sound().delay() > 0 && this.soundWait == 0) {
            this.soundWait = info.sound().delay();
        }
        int shownTicks = Math.abs(current.stats().interval());
        if (current.kind().cooldownSpansReload()) {
            shownTicks = Math.max(shownTicks, info.ammo().reloadTime() - COOLDOWN_SPANS_RELOAD_MARGIN);
        }
        if (this.currentIndex >= 0 && this.currentIndex < this.lastUsedCount.length) {
            this.lastUsedCount[this.currentIndex] = shownTicks;
        }
        this.currentIndex = (this.currentIndex + 1) % this.mounts.size();
        this.cooldown = shooter.isPlayer() ? current.stats().interval() : current.stats().paddedInterval();
        this.reloadTicks = 0;
        if (this.ammo > 0) {
            this.ammo--;
        }
        if (this.ammo > 0) {
            return FireOutcome.FIRED;
        }
        if (infiniteAmmo && this.reserve < this.magazineSize()) {
            this.setReserveAmmo(this.magazineSize());
        }
        this.reload();
        if (shooter.user() instanceof AiGunner) {
            this.cooldown += (this.cooldown >= 0 ? 1 : -1) * info.ammo().reloadTime();
        }
        return FireOutcome.FIRED_AND_RELOADING;
    }

    // Ticking

    /** Whether the mount fired recently enough to show it (muzzle flashes, the synced fire state). */
    public boolean isFiring(int index) {
        if (index >= this.lastUsedCount.length) return false;
        int interval = this.firstMount().stats().interval();
        int count = this.lastUsedCount[index];
        return (interval >= 4 && count > interval / 2) || count >= SHOWN_FIRING_TICKS;
    }

    /**
     * Once per tick on each side: reload, heat, cooldown, the mounts' own state and the barrel spin.
     *
     * @param firing whether the group fired this tick, which spins the barrels up
     */
    public void tick(boolean firing) {
        if (this.reloadTicks > 0 && --this.reloadTicks == 0) {
            this.reloadMagazine();
        }
        this.tickLastUsed();
        this.tickHeat(this.spec());
        if (this.cooldown > 0) this.cooldown--;
        else if (this.cooldown < 0) this.cooldown++;
        this.turret.rememberPrevious();
        for (WeaponMount mount : this.mounts) {
            mount.tick(this.cooldown);
        }
        if (this.soundWait > 0) this.soundWait--;
        this.barrels.tick(firing, CombatRandom.of(this.level).barrels());
    }

    private void tickLastUsed() {
        for (int i = 0; i < this.lastUsedCount.length; i++) {
            if (this.lastUsedCount[i] <= 0) continue;
            if (this.lastUsedCount[i] == SHOWN_FIRING_TICKS) {
                boolean ready = this.currentIndex == 0 && this.canFire() && (this.ammo > 0 || this.maxAmmo() <= 0);
                if (ready) this.lastUsedCount[i]--;
            } else {
                this.lastUsedCount[i]--;
            }
        }
    }

    private void tickHeat(WeaponSpec info) {
        if (this.heat > 0) {
            if (this.heat < info.missile().heat().maxHeatCount()) {
                this.heatRecovery++;
            }
            this.heat = Math.max(0, this.heat - (this.heatRecovery / 20 + 1));
        }
    }

    /** Once per tick and mount: the recoil buffer, and on the client the spent casing of a shot. */
    public void tickMount(Shooter shooter, boolean fired, int index) {
        WeaponMount mount = this.mount(index);
        Entity platform = shooter.platform();
        if (fired && platform.level().isClientSide && mount != null && mount.cartridge() != null) {
            Vec3 offset = shooter.muzzleOffset(mount);
            Vec3 m = platform.getDeltaMovement();
            SpentCases.INSTANCE.eject(platform.level(), mount.cartridge(), platform.position().add(offset), m,
                    platform.getYRot() + this.turret.yaw(), platform.getXRot() + this.turret.pitch());
        }
        if (index < this.recoil.length) {
            this.recoil[index].tick(fired);
        }
    }

    public RecoilBuffer recoil(int index) {
        return this.recoil[index];
    }

    public BarrelSpin barrels() {
        return this.barrels;
    }

    public TurretAngles turret() {
        return this.turret;
    }

    public float yaw() {
        return this.turret.yaw();
    }

    public float pitch() {
        return this.turret.pitch();
    }

    // Impact prediction

    /** The predicted impact of the current mount's shot from {@code base} along the barrels, or null. */
    @Nullable
    public Vec3 predictImpact(Shooter shooter, Vec3 base, float roll) {
        WeaponMount current = this.currentMount();
        if (current == null) return null;
        Vec2 aim = shooter.barrelAim(this, current);
        Shot shot = new Shot(this.level, shooter, current, base, Mth.wrapDegrees(aim.x), Mth.wrapDegrees(aim.y), roll,
                FireRequest.NONE, false);
        return Trajectories.impact(shot);
    }

    /** The predicted impact of the mount's shot from {@code origin} along (yaw, pitch), or null. */
    @Nullable
    public Vec3 predictImpact(Shooter shooter, WeaponMount mount, Vec3 origin, float yaw, float pitch, float roll) {
        return Trajectories.impact(new Shot(this.level, shooter, mount, origin, yaw, pitch, roll, FireRequest.NONE,
                false));
    }

    /** Horizontal distance to the predicted impact of a shot without roll, or -1. */
    public double impactDistance(Shooter shooter, Vec3 base) {
        Vec3 impact = this.predictImpact(shooter, base, 0.0F);
        if (impact == null) return -1.0;
        double dx = impact.x - base.x;
        double dz = impact.z - base.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
