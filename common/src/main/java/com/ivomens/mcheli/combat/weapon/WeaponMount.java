package com.ivomens.mcheli.combat.weapon;

import com.ivomens.mcheli.combat.guidance.LockOnSeeker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * One weapon where it is mounted: the definition, where the muzzle sits on the platform and how it is
 * angled, the weapon's kind and fire mode, and the little state a single mount keeps (its mode, the
 * ticks since its last shot, the stats a side derives from the definition).
 * <p>
 * Mounts of the same weapon fired in turn form a {@link WeaponGroup}.
 */
public final class WeaponMount {

    private final String name;
    private final WeaponSpec info;
    private final WeaponKind kind;
    private final Vec3 position;
    private final float fixedYaw;
    private final float fixedPitch;
    private final boolean onTurret;
    private final boolean client;
    private final FireMode fireMode;
    private final MountStats stats;
    private int currentMode;
    /** Ticks since the last shot, counted while the group cools down. */
    private int ticksSinceShot;
    private int airburstDistance;
    /** What the client asks the server for with this mount's shots, kept from shot to shot. */
    private FireRequest request = FireRequest.NONE;

    private WeaponMount(Level level, String name, WeaponSpec info, WeaponKind kind, Vec3 position, float yaw,
                        float pitch, boolean onTurret, @Nullable Predicate<Entity> excludedTargets) {
        this.name = name;
        this.info = info;
        this.kind = kind;
        this.position = position;
        this.fixedYaw = yaw;
        this.fixedPitch = pitch;
        this.onTurret = onTurret;
        this.client = level.isClientSide;
        this.stats = MountStats.of(info, kind, this.client);
        this.fireMode = kind.newFireMode(level, this, excludedTargets);
    }

    /**
     * A mount of the named weapon, or null if the definition or its {@code Type} is unknown.
     *
     * @param excludedTargets entities its seeker never locks, such as the crew of its own vehicle
     */
    @Nullable
    public static WeaponMount create(Level level, String weaponName, Vec3 position, float yaw, float pitch,
                                     boolean onTurret, @Nullable Predicate<Entity> excludedTargets) {
        WeaponSpec info = Weapons.get(weaponName).orElse(null);
        if (info == null || info.definition().type() == null || info.definition().type().isEmpty()) {
            return null;
        }
        WeaponKind kind = WeaponKind.ofLegacyName(info.definition().type());
        if (kind == null) {
            return null;
        }
        return new WeaponMount(level, weaponName, info, kind, position, yaw, pitch, onTurret, excludedTargets);
    }

    /** The placeholder of an empty weapon slot: it never fires. */
    static WeaponMount placeholder(Level level) {
        return new WeaponMount(level, "none", WeaponKind.placeholderInfo(), WeaponKind.DUMMY, Vec3.ZERO, 0.0F, 0.0F,
                false, null);
    }

    public String name() {
        return this.name;
    }

    public WeaponSpec spec() {
        return this.info;
    }

    public WeaponKind kind() {
        return this.kind;
    }

    /** Where the muzzle sits on the platform, in the platform's own coordinates. */
    public Vec3 position() {
        return this.position;
    }

    public float fixedYaw() {
        return this.fixedYaw;
    }

    public float fixedPitch() {
        return this.fixedPitch;
    }

    public boolean onTurret() {
        return this.onTurret;
    }

    public boolean isClientSide() {
        return this.client;
    }

    FireMode fireMode() {
        return this.fireMode;
    }

    public MountStats stats() {
        return this.stats;
    }

    /** The seeker of a lock-on weapon, or null. */
    @Nullable
    public LockOnSeeker seeker() {
        return this.fireMode.seeker();
    }

    /** The name the HUD shows, with the current mode's label. */
    public String displayName() {
        return this.info.definition().displayName() + this.kind.modeLabel(this.currentMode());
    }

    public int currentMode() {
        return this.info.fixMode() > 0 ? this.info.fixMode() : this.currentMode;
    }

    /** Cycles to the next mode; returns whether the mode changed. */
    boolean switchMode() {
        if (this.info.fixMode() > 0) {
            return false;
        }
        int previous = this.currentMode();
        int modes = this.stats.modes();
        this.currentMode = modes > 0 ? (previous + 1) % modes : 0;
        return previous != this.currentMode();
    }

    public int ticksSinceShot() {
        return this.ticksSinceShot;
    }

    void onShot() {
        this.ticksSinceShot = 0;
    }

    /** Once per tick of the group; the shot counter only runs while the group cools down. */
    void tick(int cooldown) {
        if (cooldown != 0) {
            this.ticksSinceShot++;
        }
        this.fireMode.tick(this, cooldown);
    }

    /**
     * Server: fires the mount once along (yaw, pitch) from {@code origin}, outside any group: no spread,
     * no ammo and no cooldown. Handheld launchers keep those on the item instead.
     */
    public boolean fireOnce(Shooter shooter, Vec3 origin, float yaw, float pitch, FireRequest request) {
        Shot shot = new Shot(shooter.platform().level(), shooter, this, origin, yaw, pitch, 0.0F, request, true);
        return shot.allowedByListeners() && this.fireMode.fire(shot);
    }

    public int airburstDistance() {
        return this.airburstDistance;
    }

    public void setAirburstDistance(int distance) {
        this.airburstDistance = distance;
    }

    public FireRequest request() {
        return this.request;
    }

    /** Client: what the next shot asks for; a fire mode updates the part it uses before each shot. */
    public void setRequest(FireRequest request) {
        this.request = request;
    }

    /** The spent casing the client throws out with each shot, or null. */
    @Nullable
    public Cartridge cartridge() {
        return this.client ? this.info.cartridge() : null;
    }

    /** Heavy weapons are heard over the configured vehicle sound range; small arms at the normal distance. */
    public boolean isHeavy() {
        return this.kind.isAlwaysHeavy() || this.stats.explosionPower() > 0 || this.info.explosion().power() > 0;
    }
}
