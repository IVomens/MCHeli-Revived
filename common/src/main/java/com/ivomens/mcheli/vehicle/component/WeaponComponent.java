package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.core.sim.attitude.RecoilKick;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleParts;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.entityview.AiGunner;
import com.ivomens.mcheli.combat.weapon.FireOutcome;
import com.ivomens.mcheli.combat.weapon.FireRequest;
import com.ivomens.mcheli.combat.weapon.Shooter;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.combat.weapon.WeaponSelection;
import com.ivomens.mcheli.combat.weapon.WeaponMount;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The vehicle's weapons: the weapon sets built from the definition, which one each seat has
 * selected, firing, the synced record of which weapons fired, recoil, and the per-tick update of
 * barrels and weapon bay doors. Aiming the barrels is {@link WeaponAiming}'s job, predicting where
 * shots land {@link ImpactPredictor}'s.
 */
@TickOrder(phase = TickPhase.PHYSICS)
public final class WeaponComponent implements VehicleComponent, WeaponSelection {

    /** {@link #usedWeaponBits} and the synced copy hold one bit per weapon, so only 32 are tracked. */
    private static final int MAX_TRACKED_WEAPONS = 32;
    private static final int RECOIL_TICKS = 30;
    /** Ticks the hit marker shows after one of our shots hits. */
    public static final int HIT_MARKER_TICKS = 15;

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final SyncedValue<Integer> syncedUsedWeapons;
    private final SeatsComponent seats;
    private final CrewAimComponent aim;
    private final PartsComponent parts;
    private final RollComponent roll;
    private final WeaponGroup dummy;
    private final SeatWeaponSelection selection;
    private final WeaponAiming aiming;
    private final ImpactPredictor impact;
    private ShotDirection shot;
    private WeaponGroup[] weapons = new WeaponGroup[0];
    private boolean clampsAimToTurret;
    private int recoilTicks;
    private float recoilYaw;
    private float recoilStrength;
    /** Bit per weapon that fired since the last sync (server) or in the last synced state (client). */
    private int usedWeaponBits;
    private int hitMarkerTicks;

    public WeaponComponent(AircraftEntity vehicle, StatusWord status, SyncedValue<Integer> syncedUsedWeapons,
            SeatsComponent seats, CrewAimComponent aim, TowComponent tow, PartsComponent parts, RollComponent roll,
            LifecycleComponent lifecycle, BooleanSupplier quaternionAttitude, BooleanSupplier testMode) {
        this.vehicle = vehicle;
        this.status = status;
        this.syncedUsedWeapons = syncedUsedWeapons;
        this.seats = seats;
        this.aim = aim;
        this.parts = parts;
        this.roll = roll;
        this.dummy = WeaponGroup.placeholder(vehicle.level());
        this.selection = new SeatWeaponSelection(vehicle, this, seats, testMode);
        this.aiming = new WeaponAiming(vehicle, this, aim, tow);
        this.impact = new ImpactPredictor(vehicle, this, lifecycle);
        this.shot = new ShotDirection(vehicle, this, aim, roll, quaternionAttitude);
    }

    /**
     * The weapons are aimed in world space rather than relative to the hull: the gunner's view is
     * the barrel's direction, and the hull is briefly turned to it for each shot.
     */
    public void aimInWorldSpace() {
        this.shot = new WorldShotDirection(this.vehicle, this, this.aim, this.roll);
    }

    boolean aimsInWorldSpace() {
        return this.shot instanceof WorldShotDirection;
    }

    /** Every shot is clamped to the turret's arc and pitch limits, measured from the hull (tanks). */
    public void clampAimToTurret() {
        this.clampsAimToTurret = true;
    }

    boolean clampsAimToTurret() {
        return this.clampsAimToTurret;
    }

    /** The user firing this vehicle's weapons, as the weapons see it. */
    public Shooter shooter(@Nullable Entity user) {
        return new VehicleShooter(this.vehicle, this, this.aim, this.seats, user);
    }

    public WeaponGroup[] all() {
        return this.weapons;
    }

    public int count() {
        return this.weapons.length;
    }

    /** The weapon group with this id, or an empty placeholder group for an invalid id. */
    public WeaponGroup get(int id) {
        return id >= 0 && id < this.weapons.length ? this.weapons[id] : this.dummy;
    }

    @Nullable
    public WeaponGroup byName(String name) {
        for (WeaponGroup weapon : this.weapons) {
            if (weapon.isNamed(name)) {
                return weapon;
            }
        }
        return null;
    }

    @Nullable
    public WeaponSpec infoById(int id) {
        return id >= 0 ? get(id).spec() : null;
    }

    /** The weapon the entity's seat has selected, or the placeholder group. */
    @Override
    public WeaponGroup current(@Nullable Entity entity) {
        return get(currentId(entity));
    }

    public int currentId(@Nullable Entity entity) {
        return this.selection.currentId(entity);
    }

    /** The definition's mount of the entity's current weapon. */
    @Nullable
    public VehicleParts.Weapon currentMount(@Nullable Entity entity) {
        VehicleSpec info = this.vehicle.spec();
        int weaponId = currentId(entity);
        return info != null && weaponId >= 0 ? info.armament().getWeaponById(weaponId) : null;
    }

    public int idForSeat(int seatId) {
        return this.selection.idForSeat(seatId);
    }

    /** Next weapon (in the given direction) that the entity's seat can use, or -1 if there is none. */
    public int nextId(@Nullable Entity entity, int step) {
        return this.selection.nextId(entity, step);
    }

    public void select(Entity entity, int id) {
        this.selection.select(entity, id);
    }

    /** Applies a seat's weapon selection received from the other side. */
    public void applySelection(int seatId, int id) {
        this.selection.applySelection(seatId, id);
    }

    /** Selects the first usable weapon for a rider who just took a seat. */
    public void selectFirst(Entity entity) {
        this.selection.selectFirst(entity);
    }

    public void clearPilotSelection() {
        this.selection.clearPilot();
    }

    public WeaponGroup pilotWeapon() {
        return get(Math.max(this.selection.pilotWeaponId(), 0));
    }

    /** The seat a weapon is operated from, see {@link SeatWeaponSelection#seatOf}. */
    public int seatOf(@Nullable WeaponSpec weaponInfo, VehicleParts.Weapon mount) {
        return this.selection.seatOf(weaponInfo, mount);
    }

    @Nullable
    Entity operator(int weaponId, VehicleParts.Weapon mount) {
        return this.selection.operator(weaponId, mount);
    }

    /** Who fires the named weapon: its seat's rider, or the pilot if the seat is empty and the pilot may. */
    @Nullable
    public Entity userOf(String name) {
        VehicleSpec info = this.vehicle.spec();
        VehicleParts.Weapon mount = info != null ? info.armament().getWeaponByName(name) : null;
        if (mount == null) {
            return null;
        }
        Entity user = this.seats.riderOf(seatOf(null, mount));
        return user == null && mount.canUsePilot ? this.vehicle.getRiddenByEntity() : user;
    }

    @Nullable
    public Player playerUsing(int weaponId) {
        return this.selection.playerUsing(weaponId);
    }

    /** Creative crew and the server's infinite ammo setting never run dry. */
    public boolean hasInfiniteAmmo(@Nullable Entity user) {
        return Creative.isCreative(user) || this.status.get(StatusWord.INFINITE_AMMO);
    }

    public ShotDirection shot() {
        return this.shot;
    }

    /** One of our shots hit something: the HUD shows the hit marker. */
    public void markHit() {
        this.hitMarkerTicks = HIT_MARKER_TICKS;
    }

    public int hitMarkerTicks() {
        return this.hitMarkerTicks;
    }

    public WeaponAiming aiming() {
        return this.aiming;
    }

    public ImpactPredictor impact() {
        return this.impact;
    }

    public RecoilKick recoil() {
        return new RecoilKick(this.recoilTicks, this.recoilYaw, this.recoilStrength);
    }

    /** The kick fades one tick at a time, after the physics step. */
    public void tickRecoil() {
        if (this.recoilTicks > 0) {
            this.recoilTicks--;
        }
    }

    static boolean isOperator(@Nullable Entity entity) {
        return entity instanceof Player || entity instanceof AiGunner;
    }

    /** Builds the weapon groups from the definition and resets every seat's selection. */
    public WeaponGroup[] createWeapons(int seatCount) {
        this.weapons = buildWeapons(seatCount);
        return this.weapons;
    }

    private WeaponGroup[] buildWeapons(int seatCount) {
        this.selection.reset(seatCount);

        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        if (info == null || info.armament().weaponSets().isEmpty() || seatCount <= 0) {
            return new WeaponGroup[]{this.dummy};
        }

        WeaponGroup[] groups = new WeaponGroup[info.armament().weaponSets().size()];
        for (int i = 0; i < groups.length; i++) {
            VehicleParts.WeaponSet setInfo = info.armament().weaponSets().get(i);
            List<WeaponMount> mounts = new ArrayList<>(setInfo.weapons.size());
            for (VehicleParts.Weapon mount : setInfo.weapons) {
                // a seeker never locks on to this vehicle's own crew
                mounts.add(Objects.requireNonNull(WeaponMount.create(ac.level(), setInfo.type, mount.pos, mount.yaw,
                        mount.pitch, mount.turret, this.seats::isAboard)));
            }
            if (!mounts.isEmpty()) {
                float defaultYaw = setInfo.weapons.getFirst().defaultYaw;
                groups[i] = new WeaponGroup(ac.level(), mounts);
                groups[i].turret().setPrevYaw(defaultYaw);
                groups[i].turret().setYaw(defaultYaw);
                groups[i].turret().setDefaultYaw(defaultYaw);
            }
        }
        return groups;
    }

    private Vec3 vehiclePosition() {
        return new Vec3(this.vehicle.getX(), this.vehicle.getY(), this.vehicle.getZ());
    }

    /**
     * Client-side fire prediction. Weapons sharing the fired weapon's group also get its delay and
     * reload here, mirroring the server, so their HUD timers stay in step instead of reading 0.
     */
    public boolean prepareFire(@Nullable Entity user) {
        AircraftEntity ac = this.vehicle;
        if (user == null) {
            return false;
        }
        WeaponGroup current = current(user);
        FireOutcome outcome = current.predictFire(shooter(user), vehiclePosition(), hasInfiniteAmmo(user));
        if (!outcome.fired()) {
            return false;
        }
        shareCooldownWithGroup(current, outcome.reloading());
        return true;
    }

    /** Fires the user's current weapon without a mode or lock. */
    public boolean fire(Entity user) {
        return fire(user, FireRequest.NONE);
    }

    /** Fires the user's current weapon from the vehicle's position, as a gunner does. */
    public boolean fire(Entity user, FireRequest request) {
        return fire(user, vehiclePosition(), request);
    }

    /** Fires the user's current weapon from {@code from} (where the client saw the vehicle). */
    public boolean fire(@Nullable Entity user, Vec3 from, FireRequest request) {
        if (this.shot instanceof WorldShotDirection) {
            return fireAlongGunnerView(user, from, request);
        }
        return fireCurrent(user, from, request);
    }

    /** World-aimed weapons fire along the gunner's view, so the hull is briefly turned to it. */
    private boolean fireAlongGunnerView(@Nullable Entity user, Vec3 from, FireRequest request) {
        AircraftEntity ac = this.vehicle;
        float pitch = ac.getXRot();
        float yaw = ac.getYRot();
        ac.setXRot(this.aim.userPitch(user));
        ac.setYRot(this.aim.userYaw(user));
        boolean fired = fireCurrent(user, from, request);
        ac.setXRot(pitch);
        ac.setYRot(yaw);
        return fired;
    }

    private boolean fireCurrent(@Nullable Entity user, Vec3 from, FireRequest request) {
        AircraftEntity ac = this.vehicle;
        boolean infiniteAmmo = hasInfiniteAmmo(user);
        if (user == null) {
            return false;
        }
        WeaponGroup current = current(user);
        if (!current.canFire()) {
            return false;
        }

        int firedIndex = current.currentIndex();
        FireOutcome outcome = current.fire(shooter(user), from, request, infiniteAmmo);
        if (!outcome.fired()) {
            return false;
        }
        shareCooldownWithGroup(current, outcome.reloading());

        if (!ac.level().isClientSide) {
            int bit = getWeaponBitOffset(current) + firedIndex;
            if (bit < MAX_TRACKED_WEAPONS) {
                this.usedWeaponBits |= 1 << bit;
            }
            if (outcome.reloading() && user instanceof ServerPlayer player) {
                syncReloadToShooter(player, current);
            }
        }
        return true;
    }

    private void shareCooldownWithGroup(WeaponGroup fired, boolean reload) {
        String group = fired.spec().definition().group();
        if (group == null || group.isEmpty()) {
            return;
        }
        for (WeaponGroup weapon : this.weapons) {
            if (weapon != fired && group.equals(weapon.spec().definition().group())) {
                weapon.followGroupShot(reload);
            }
        }
    }

    /** After a reload the server's timers differ from the client's prediction; send the shooter the real ones. */
    private void syncReloadToShooter(ServerPlayer player, WeaponGroup fired) {
        int seatId = this.seats.seatIdOf(player);
        if (!this.seats.isValidSeatId(seatId)) {
            return;
        }
        String group = fired.spec().definition().group();
        boolean hasGroup = group != null && !group.isEmpty();
        WeaponGroup[] weapons = this.weapons;
        for (int i = 0; i < weapons.length; i++) {
            WeaponGroup weapon = weapons[i];
            if (weapon == fired || hasGroup && group.equals(weapon.spec().definition().group())) {
                Network.sendToPlayer(player, this.selection.stateMessage(seatId, i, weapon));
            }
        }
    }

    private int getWeaponBitOffset(WeaponGroup target) {
        int offset = 0;
        for (WeaponGroup weapon : this.weapons) {
            if (weapon == target) {
                break;
            }
            offset += weapon.mountCount();
        }
        return offset;
    }

    private int usedWeaponStat() {
        VehicleSpec info = this.vehicle.spec();
        if (info == null || info.armament().getWeaponCount() <= 0) {
            return 0;
        }
        int stat = 0;
        int bit = 0;
        for (WeaponGroup weapon : this.weapons) {
            for (int i = 0; i < weapon.mountCount() && bit < MAX_TRACKED_WEAPONS; i++, bit++) {
                if (weapon.isFiring(i)) {
                    stat |= 1 << bit;
                }
            }
        }
        return stat;
    }

    public boolean isOnCooldown(WeaponGroup weapon, int index) {
        VehicleSpec info = this.vehicle.spec();
        if (info == null || info.armament().getWeaponCount() <= 0) {
            return true;
        }
        return (this.usedWeaponBits & 1 << getWeaponBitOffset(weapon) + index) == 0;
    }

    private boolean hasWeapons() {
        VehicleSpec info = this.vehicle.spec();
        return info != null && info.armament().getWeaponCount() > 0;
    }

    /** Server: publishes which weapons fired since the last tick, then updates them. */
    @Override
    public void serverTick(TickContext ctx) {
        if (!hasWeapons()) {
            return;
        }
        int previousUsedBits = this.usedWeaponBits;
        this.usedWeaponBits |= usedWeaponStat();
        this.syncedUsedWeapons.set(this.usedWeaponBits);
        this.usedWeaponBits = 0;
        update(previousUsedBits, false);
    }

    /** Client: takes over which weapons fired from the server, then updates them with muzzle flashes. */
    @Override
    public void clientTick(TickContext ctx) {
        if (!hasWeapons()) {
            return;
        }
        int previousUsedBits = this.usedWeaponBits;
        this.usedWeaponBits = this.syncedUsedWeapons.get();
        update(previousUsedBits, true);
    }

    private void update(int previousUsedBits, boolean muzzleFlashes) {
        AircraftEntity ac = this.vehicle;
        VehicleSpec info = ac.spec();
        float yaw = Mth.wrapDegrees(ac.getYRot());
        float pitch = Mth.wrapDegrees(ac.getXRot());
        WeaponGroup[] weapons = this.weapons;
        Shooter unmanned = shooter(null);
        int bit = 0;
        for (int weaponId = 0; weaponId < weapons.length; weaponId++) {
            WeaponGroup weapon = weapons[weaponId];
            boolean longDelay = weapon.hasLongDelay();
            boolean anyFired = false;

            for (int index = 0; index < weapon.mountCount(); index++, bit++) {
                boolean firedBefore = bit < MAX_TRACKED_WEAPONS && (previousUsedBits & 1 << bit) != 0;
                boolean fired = bit < MAX_TRACKED_WEAPONS && (this.usedWeaponBits & 1 << bit) != 0;
                // a long-delay weapon reports the same shot over several syncs; count it once
                if (longDelay && firedBefore && fired) {
                    fired = false;
                }
                anyFired |= fired;
                if (!firedBefore && fired) {
                    startRecoil(weapon);
                }
                if (fired && muzzleFlashes) {
                    MuzzleFlashes.spawn(ac, this, weapon, yaw);
                }
                weapon.tickMount(unmanned, fired, index);
            }

            weapon.tick(anyFired);
            VehicleParts.Weapon mount = info.armament().getWeaponById(weaponId);
            if (mount != null && !ac.isDestroyed()) {
                this.aiming.aim(weapon, mount, weaponId, yaw, pitch, true);
            }
        }

        tickWeaponBays(info);
        if (this.hitMarkerTicks > 0) {
            this.hitMarkerTicks--;
        }
    }

    private void startRecoil(WeaponGroup weapon) {
        float recoil = weapon.spec().recoil().power();
        if (recoil > 0.0F) {
            this.recoilTicks = RECOIL_TICKS;
            this.recoilStrength = recoil;
            this.recoilYaw = weapon.yaw();
        }
    }

    /** Bay doors open while a crew member has one of the bay's weapons selected. */
    private void tickWeaponBays(VehicleSpec info) {
        for (int i = 0; i < this.parts.weaponBayCount(); i++) {
            this.parts.weaponBay(i).step(this.selection.isAnySelectedByRider(info.parts().weaponBays().get(i).weaponIds));
        }
    }
}
