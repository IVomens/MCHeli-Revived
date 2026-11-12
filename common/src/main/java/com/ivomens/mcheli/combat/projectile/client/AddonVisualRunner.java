package com.ivomens.mcheli.combat.projectile.client;

import com.ivomens.mcheli.api.McheliApi;
import com.ivomens.mcheli.api.client.weapon.ProjectileVisual;
import com.ivomens.mcheli.api.client.weapon.ProjectileVisualContext;
import com.ivomens.mcheli.api.content.WeaponInfo;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.combat.projectile.behaviour.ProjectileVisualHooks;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The addon visuals of one projectile on a client, and the {@link ProjectileVisualContext} they see. A visual that
 * throws is logged and skipped for the rest of the projectile's life.
 */
final class AddonVisualRunner implements ProjectileVisualHooks.Visuals, ProjectileVisualContext {

    private final ProjectileEntity projectile;
    private final WeaponSpec weapon;
    private final List<AddonVisuals.Entry> entries;
    @Nullable
    private WeaponInfo weaponInfo;
    @Nullable
    private CompoundTag data;
    /** One bit per visual that threw. */
    private long failed;

    AddonVisualRunner(ProjectileEntity projectile, WeaponSpec weapon, List<AddonVisuals.Entry> entries) {
        this.projectile = projectile;
        this.weapon = weapon;
        this.entries = entries;
    }

    @Override
    public void tick() {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.runs(i)) {
                try {
                    this.entries.get(i).visual().onTick(this);
                } catch (RuntimeException e) {
                    this.fail(i, e);
                }
            }
        }
    }

    @Override
    public void impact(HitResult hit) {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.runs(i)) {
                try {
                    this.entries.get(i).visual().onImpact(this, hit);
                } catch (RuntimeException e) {
                    this.fail(i, e);
                }
            }
        }
    }

    @Override
    public void removed() {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.runs(i)) {
                try {
                    this.entries.get(i).visual().onRemoved(this);
                } catch (RuntimeException e) {
                    this.fail(i, e);
                }
            }
        }
    }

    private boolean runs(int index) {
        return index >= Long.SIZE || (this.failed & 1L << index) == 0;
    }

    private void fail(int index, RuntimeException e) {
        if (index < Long.SIZE) {
            this.failed |= 1L << index;
        }
        ProjectileVisual visual = this.entries.get(index).visual();
        ModLogger.error("Projectile visual " + this.entries.get(index).id() + " (" + visual.getClass().getName()
                + ") of weapon " + this.weapon.name() + " failed; it is skipped for this projectile", e);
    }

    @Override
    public Entity entity() {
        return this.projectile;
    }

    @Override
    public Level level() {
        return this.projectile.level();
    }

    @Override
    public WeaponInfo weapon() {
        if (this.weaponInfo == null) {
            this.weaponInfo = McheliApi.get().content().weapon(this.weapon.name())
                    .orElseThrow(() -> new IllegalStateException("weapon " + this.weapon.name() + " is not loaded"));
        }
        return this.weaponInfo;
    }

    @Override
    public Vec3 position() {
        return this.projectile.position();
    }

    @Override
    public Vec3 velocity() {
        return this.projectile.getDeltaMovement();
    }

    @Override
    public int ticksFlown() {
        return this.projectile.age();
    }

    @Override
    @Nullable
    public Entity target() {
        return this.projectile.target();
    }

    @Override
    public CompoundTag data() {
        if (this.data == null) {
            this.data = new CompoundTag();
        }
        return this.data;
    }
}
