package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.combat.weapon.Weapons;
import com.ivomens.mcheli.config.McheliConfig;
import com.ivomens.mcheli.core.ballistics.ProjectileSteps;
import com.ivomens.mcheli.entity.McheliEntity;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Every MCHeli projectile: rounds, rockets, missiles, bombs, torpedoes, marker rockets and dropped
 * containers. One class for all of them; what differs is the {@link ProjectileKind} (the entity type it
 * was registered as) and the weapon definition it flies for.
 * <p>
 * A tick runs in a fixed order, the same for every kind:
 * <ol>
 *   <li>bookkeeping: trail positions, the target on a client, launch boost and chunk leases on the server;</li>
 *   <li>fuzes on the server: unloaded chunks, delay and time fuzes, range, altitude ({@link Fuzes});</li>
 *   <li>forces: boost phase, gravity, drag ({@link FlightModel});</li>
 *   <li>collisions with blocks and entities, then the airburst and proximity fuzes;</li>
 *   <li>the move itself, then the bomblet spreader;</li>
 *   <li>the kind's own steering, trail and bomblet timer.</li>
 * </ol>
 * Projectiles never load chunks, and are not saved with the world (except laid mines); one that waits in a
 * chunk that ticks no entities for longer than the frozen lifetime is removed ({@link ProjectileLifetime}).
 */
public final class ProjectileEntity extends McheliEntity {

    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(ProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> WEAPON_NAME =
            SynchedEntityData.defineId(ProjectileEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> BOMBLET_FLAG =
            SynchedEntityData.defineId(ProjectileEntity.class, EntityDataSerializers.BYTE);
    /** Kind specific: the marker rocket's status, a mine's flags. */
    private static final EntityDataAccessor<Byte> ROLE_STATE =
            SynchedEntityData.defineId(ProjectileEntity.class, EntityDataSerializers.BYTE);

    /** Speed the entity is capped to; faster weapons move several steps per tick instead. */
    static final double MAX_SPEED = 3.9;
    private static final int TARGET_ALERT_INTERVAL = 20;
    private static final int MAX_AGE_COUNT = 10_000_000;

    private final ProjectileKind kind;
    private final Fuzes fuzes = new Fuzes(this);
    private final RoleBehaviour role;
    final ProjectileLifetime lifetime;
    @Nullable
    private WeaponSpec weaponSpec;
    @Nullable
    private Entity shooter;
    @Nullable
    private Entity platform;
    @Nullable
    private Entity target;
    private final Warhead warhead = new Warhead();
    /** The addon behaviours its weapon names, or null for a weapon that names none (every bundled one). */
    @Nullable AddonProjectile addon;
    /** What clients were told about the flight; see {@link ProjectileSync}. */
    final ProjectileSync.State sync = new ProjectileSync.State();
    /** Speed along the flight direction; the motion is this long unless gravity or steering changed it. */
    private double speed = 1.0;
    /** Movement steps per tick: fast weapons move several, see {@link ProjectileKind#movesInSteps}. */
    private double stepFactor = 1.0;
    /** -1 unknown (a client before the sync), 0 fired by a weapon, 1 a sub-munition of a cluster weapon. */
    private byte bombletState = -1;
    /** The countdowns of a cluster weapon's projectile, kept for {@link Submunition}. */
    final ClusterCounters cluster = new ClusterCounters();
    private int age;
    private boolean speedAddedFromCarrier;
    private boolean topAttackClimb;
    private boolean topAttack;
    private boolean trailHidden;
    @Nullable
    private Vec3 targetPoint;
    /** Where the projectile was at the start of the tick before last: the tail of the smoke trail. */
    Vec3 trailTail = Vec3.ZERO;
    private Vec3 lastTickStart = Vec3.ZERO;

    public ProjectileEntity(EntityType<ProjectileEntity> type, Level level) {
        super(type, level);
        this.kind = ProjectileKind.of(type);
        this.role = RoleBehaviour.of(this.kind, this);
        this.lifetime = new ProjectileLifetime(this, this.role);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    /**
     * A projectile of the kind at {@code pos}, facing (yaw, pitch) and moving at {@code speed} (capped to
     * {@value #MAX_SPEED}) along {@code direction}. Not yet named after a weapon nor added to the level.
     */
    public static ProjectileEntity create(ProjectileKind kind, Level level, Vec3 pos, Vec3 direction, float yaw,
                                          float pitch, double speed) {
        ProjectileEntity projectile = new ProjectileEntity(kind.entityType(), level);
        projectile.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        projectile.yRotO = yaw;
        projectile.xRotO = pitch;
        projectile.speed = Math.min(speed, MAX_SPEED);
        projectile.setMotionAlong(direction.x, direction.y, direction.z);
        return projectile;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(TARGET_ID, 0);
        builder.define(WEAPON_NAME, "");
        builder.define(BOMBLET_FLAG, (byte) 0);
        builder.define(ROLE_STATE, (byte) 0);
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        super.moveTo(x, y, z, yaw, pitch);
        this.trailTail = new Vec3(x, y, z);
        this.lastTickStart = this.trailTail;
    }

    /** Client interpolation: smooth the height only, the rotation is derived from the motion locally. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, (y + this.getY() * 2.0) / 3.0, z);
    }

    public ProjectileKind kind() {
        return this.kind;
    }

    /** Name of the weapon definition ({@code Entity#getName} is taken by the display name). */
    public String weaponName() {
        return this.entityData.get(WEAPON_NAME);
    }

    /** Takes the stats of the named weapon; unknown names are ignored. */
    public void setWeapon(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        this.weaponSpec = Weapons.get(name).orElse(null);
        if (this.weaponSpec == null) {
            return;
        }
        if (!this.level().isClientSide) {
            this.entityData.set(WEAPON_NAME, name);
            this.bombletState = 0;
        }
        WeaponSpec info = this.weaponSpec;
        this.addon = AddonProjectile.of(this, info);
        if (info.submunition().count() > 0) {
            this.cluster.releaseTimer = info.bombletSTime();
        }
        this.warhead.piercing = info.ballistics().piercing();
        if (this.kind.movesInSteps() && info.ballistics().acceleration() > ProjectileSteps.SPEED_PER_STEP) {
            this.stepFactor = ProjectileSteps.stepFactor(true, info.ballistics().acceleration());
        }
    }

    @Nullable
    public WeaponSpec spec() {
        return this.weaponSpec;
    }

    /** Who fired it, credited with its hits. */
    @Nullable
    public Entity shooter() {
        return this.shooter;
    }

    /** What it was fired from: the vehicle, or the shooter itself for handheld weapons. */
    @Nullable
    public Entity platform() {
        return this.platform;
    }

    /** Sets who fired the projectile and from what; its own hits and blasts spare them. */
    public void setShooter(@Nullable Entity shooter, @Nullable Entity platform) {
        this.shooter = shooter;
        this.platform = platform;
    }

    public Warhead warhead() {
        return this.warhead;
    }

    public Fuzes fuzes() {
        return this.fuzes;
    }

    /** Marks this as a sub-munition of a cluster weapon: it never splits again. */
    void markBomblet() {
        this.bombletState = 1;
        this.cluster.releaseTimer = 0;
        this.entityData.set(BOMBLET_FLAG, (byte) 1);
    }

    /** The synced flag, 1 for a sub-munition; what a client knows before {@link #isBomblet} is settled. */
    public byte syncedBombletFlag() {
        return this.entityData.get(BOMBLET_FLAG);
    }

    /** Whether this is known to be a sub-munition of a cluster weapon. */
    public boolean isBomblet() {
        return this.bombletState == 1;
    }

    /** Server: a marker rocket is fired; one that never was goes away on its first tick. */
    public void startMarkerFlight() {
        if (!this.level().isClientSide) {
            this.setRoleState(MarkerSmoke.FLYING);
        }
    }

    @Nullable
    public Entity target() {
        return this.target;
    }

    /** Sets the target on the server and tells the clients; a player on foot hears a missile alert. */
    public void setTarget(@Nullable Entity entity) {
        this.target = entity;
        HomingProjectiles.of(this.level()).targetChanged(this);
        if (!this.level().isClientSide) {
            MissileAlert.warnIfOnFoot(entity);
            this.entityData.set(TARGET_ID, entity != null ? entity.getId() : 0);
        }
    }

    /** Takes the target an active radar found, without the sync and alert of {@link #setTarget}. */
    public void setTargetUnannounced(@Nullable Entity scanned) {
        this.target = scanned;
        HomingProjectiles.of(this.level()).targetChanged(this);
    }

    public int targetId() {
        return this.target != null ? this.target.getId() : this.entityData.get(TARGET_ID);
    }

    /** The point a point-attack missile, a laser guided missile or a guided torpedo flies to. */
    @Nullable
    public Vec3 targetPoint() {
        return this.targetPoint;
    }

    public void aimAt(Vec3 point) {
        this.targetPoint = point;
    }

    /** Anti-tank missiles fired in top-attack mode climb over the target and dive onto it. */
    public boolean isTopAttack() {
        return this.topAttack;
    }

    public void setTopAttack(boolean topAttack) {
        this.topAttack = topAttack;
    }

    /** True during a top attack's climb, when the seeker may look further off the nose than usual. */
    public boolean isClimbing() {
        return this.topAttackClimb;
    }

    public void setClimbing(boolean climbing) {
        this.topAttackClimb = climbing;
    }

    public double speed() {
        return this.speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    /** Movement steps per tick: fast weapons move several. */
    public double stepFactor() {
        return this.stepFactor;
    }

    /** Sets the motion to {@link #speed} along the direction. */
    public void setMotionAlong(double dirX, double dirY, double dirZ) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        this.setDeltaMovement(dirX * this.speed / length, dirY * this.speed / length, dirZ * this.speed / length);
    }

    /** Ticks flown, counted from the first tick. */
    public int age() {
        return this.age;
    }

    public boolean isTrailHidden() {
        return this.trailHidden;
    }

    /** Hides the smoke trail, for the crew watching through this missile's camera. */
    public void setTrailHidden(boolean hidden) {
        this.trailHidden = hidden;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    byte roleState() {
        return this.entityData.get(ROLE_STATE);
    }

    void setRoleState(byte state) {
        if (this.entityData.get(ROLE_STATE) != state) {
            this.entityData.set(ROLE_STATE, state);
        }
    }

    @Override
    public void tick() {
        if (this.lifetime.endsBeforeTick()) {
            return;
        }
        if (this.addon != null) this.addon.beforeTick();
        if (!this.role.tickInstead()) {
            this.fly();
        }
        if (this.addon != null) this.addon.afterTick();
        ProjectileSync.afterTick(this);
    }

    /** One tick of flight and, unless it ended it, the kind's own steering, trail and bomblet timer. */
    void fly() {
        this.flyOneTick();
        if (!this.isRemoved() && this.weaponSpec != null) {
            this.steer();
        }
    }

    /** One tick of flight without the kind's own steering (the marker rocket and a falling mine stop here). */
    void flyOneTick() {
        Level level = this.level();
        if (!level.isClientSide && this.lifetime.isLoadedFromSave()) {
            this.discard();
            return;
        }
        this.trailTail = this.lastTickStart;
        this.lastTickStart = this.position();
        if (level.isClientSide) {
            if (this.age == 0 && this.targetId() > 0) {
                this.setTarget(level.getEntity(this.targetId()));
            }
        } else {
            this.prepareServerTick();
        }
        if (++this.age > MAX_AGE_COUNT) {
            this.age = 0;
        }
        super.tick();
        if (this.isRemoved() || !this.resolveWeapon()) return;
        this.flyResolved(this.weaponSpec);
    }

    private void flyResolved(WeaponSpec info) {
        Level level = this.level();
        if (info.bound() <= 0.0F && this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.9, 1.0, 0.9));
        }
        if (level.isClientSide && this.bombletState < 0) {
            this.bombletState = this.syncedBombletFlag();
        }
        if (!level.isClientSide && !this.fuzes.checkServer(info)) return;
        FlightModel.applyForces(this, info);
        if (!level.isClientSide && !FlightModel.isNextStepLoaded(this, info)) return;
        Collisions.collide(this);
        if (!this.isRemoved()) this.fuzes.checkAirburst(info);
        if (!this.isRemoved()) this.fuzes.checkProximity(info);
        if (this.isRemoved()) return;
        FlightModel.move(this);
        Submunition.spread(this, info);
    }

    /**
     * The kind's own part of the tick. Rockets (and the TV missiles that split into rockets) release their
     * bomblets before steering, everything else after.
     */
    private void steer() {
        Submunition submunition = this.kind.submunition();
        boolean splitsFirst = submunition == Submunition.ROCKETS;
        if (splitsFirst) {
            Submunition.tickRelease(this);
        }
        if (this.level().isClientSide) {
            this.kind.trail().emit(this);
        }
        if (this.addon == null || !this.addon.steer()) this.kind.guidance().steer(this);
        if (!splitsFirst && submunition != Submunition.NONE && !this.isRemoved()) {
            Submunition.tickRelease(this);
        }
    }

    /** Server: the one-off launch boost from the carrier, chunk leases and the alert of a player on foot. */
    private void prepareServerTick() {
        WeaponSpec info = this.weaponSpec;
        if (info != null && info.ballistics().speedDependsAircraft() && !this.speedAddedFromCarrier
                && this.platform instanceof Vehicle) {
            this.speed += this.platform.getDeltaMovement().length();
            Vec3 motion = this.getDeltaMovement();
            double length = motion.length();
            if (length > 0) {
                this.setDeltaMovement(motion.scale(this.speed / length));
            }
            this.speedAddedFromCarrier = true;
            ProjectileSync.disturbed(this);
        }
        if (info != null && (info.definition().canLoadChunks() || McheliConfig.network().projectileChunkLoad())) {
            ProjectilePaths.request(this);
        }
        if (this.age % TARGET_ALERT_INTERVAL == TARGET_ALERT_INTERVAL - 1) {
            MissileAlert.warnIfOnFoot(this.target);
        }
    }

    /**
     * Looks up the weapon from the synced name if needed. Returns false while it is unknown; a projectile
     * whose weapon is still unknown on its second tick is removed (a client's copy that started in mid-flight
     * looks its weapon up first).
     */
    private boolean resolveWeapon() {
        if (this.weaponSpec == null) {
            this.setWeapon(this.weaponName());
        }
        if (this.weaponSpec != null) {
            return true;
        }
        if (this.age >= 2) {
            ModLogger.log(this, "Removing projectile %d (%s) with unknown weapon '%s'", this.getId(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()).toString(), this.weaponName());
            this.discard();
        }
        return false;
    }

    /** The projectile hits something (a block, an entity, or a point where a fuze set it off). */
    public void impact(HitResult hit) {
        if (!this.role.onImpact(hit)) {
            this.warhead.impact(this, hit);
        }
    }

    /** Sets the projectile off where it is, as if it hit the block below. */
    public void detonateHere() {
        this.impact(new BlockHitResult(this.position(), Direction.DOWN, this.blockPosition(), false));
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket(@NotNull ServerEntity tracker) {
        return ProjectileSync.spawnPacket(this, tracker);
    }

    @Override
    public void recreateFromPacket(@NotNull ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        ProjectileSync.readSpawnPacket(this, packet);
    }

    /** Client: a copy that starts in mid-flight takes the ticks the server's projectile has flown. */
    void setTicksFlown(int ticks) {
        this.age = this.tickCount = ticks;
    }

    /** Addon behaviours hear of a projectile that ends without going off, or leaves a client. */
    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (this.addon != null) this.addon.removed();
        super.remove(reason);
    }

    @Override
    public void onClientRemoval() {
        if (this.addon != null) this.addon.removed();
        super.onClientRemoval();
    }

    /** Thrown items (snowballs, grenades...) set the projectile off. */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (!Fuzes.setsOff(this, source, amount)) return false;
        this.markHurt();
        this.detonateHere();
        return true;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        InteractionResult result = this.role.interact(player, hand);
        return result != null ? result : super.interact(player, hand);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 1.0F;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = this.getBoundingBox().getSize() * 4.0 * 64.0 * getViewScale();
        return distance < range * range;
    }

    /** Only laid mines are written to the world's saves; every other projectile ends where its chunk unloads. */
    @Override
    public boolean saveAsPassenger(@NotNull CompoundTag tag) {
        return this.lifetime.persists() && super.saveAsPassenger(tag);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        this.lifetime.save(tag);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        this.lifetime.load(tag);
    }
}
