package com.ivomens.mcheli.combat.projectile;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.combat.weapon.MineDefuserItem;
import com.ivomens.mcheli.combat.weapon.WeaponSpec;
import com.ivomens.mcheli.content.def.weapon.MineFuze;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A bomb whose weapon has a mine fuze: it settles where it lands, arms after a delay and goes off when
 * a player or vehicle comes close. Sneaking players only set a player mine off when they step right
 * onto it. Mines are saved with the world, unlike every other projectile, and wait as long as it takes.
 */
final class LandMine implements RoleBehaviour {

    private static final byte LANDED_BIT = 1;
    private static final double SNEAK_TRIGGER_DIST_SQ = 1.0;
    private static final int SCAN_INTERVAL = 5;

    private final ProjectileEntity bomb;
    private boolean armed;
    private int armTimer;
    private int ticksArmed;

    LandMine(ProjectileEntity bomb) {
        this.bomb = bomb;
    }

    private boolean isMine() {
        WeaponSpec info = this.bomb.spec();
        return info != null && info.isMine();
    }

    private boolean isLanded() {
        return (this.bomb.roleState() & LANDED_BIT) != 0;
    }

    private void setLanded(boolean landed) {
        byte state = this.bomb.roleState();
        this.bomb.setRoleState((byte) (landed ? (state | LANDED_BIT) : (state & ~LANDED_BIT)));
    }

    /** A mine falls without the bomb's drag and fuze, and once it landed only waits for its victim. */
    @Override
    public boolean tickInstead() {
        if (!this.isMine()) {
            return false;
        }
        if (this.isLanded()) {
            this.tickLanded(this.bomb.spec());
        } else {
            this.bomb.flyOneTick();
        }
        return true;
    }

    @Override
    public boolean onImpact(HitResult hit) {
        if (this.bomb.level().isClientSide || !this.isMine() || this.isLanded()) {
            return false;
        }
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            this.land(hit);
        }
        return true;
    }

    private void land(HitResult hit) {
        Vec3 pos = hit.getLocation();
        this.bomb.setPos(pos.x, pos.y, pos.z);
        this.bomb.setDeltaMovement(Vec3.ZERO);
        this.bomb.hasImpulse = true;
        this.armed = false;
        this.armTimer = 0;
        this.ticksArmed = 0;
        this.setLanded(true);
    }

    private void tickLanded(WeaponSpec info) {
        this.bomb.setDeltaMovement(Vec3.ZERO);
        if (this.bomb.level().isClientSide) {
            return;
        }
        if (!this.armed) {
            if (++this.armTimer >= info.mineArmDelay()) {
                this.armed = true;
            }
            return;
        }
        if (++this.ticksArmed % SCAN_INTERVAL == 0) {
            this.scan(info);
        }
    }

    private void scan(WeaponSpec info) {
        double range = info.mineRange();
        if (range <= 0.0D) {
            return;
        }
        double rangeSq = range * range;
        List<Entity> nearby = this.bomb.level().getEntities(this.bomb, this.bomb.getBoundingBox().inflate(range),
                entity -> triggers(entity, info));
        for (Entity entity : nearby) {
            double dx = entity.getX() - this.bomb.getX();
            double dy = (entity.getY() + entity.getBbHeight() * 0.5) - (this.bomb.getY() + this.bomb.getBbHeight() * 0.5);
            double dz = entity.getZ() - this.bomb.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > rangeSq) {
                continue;
            }
            if (info.definition().mine().fuze() == MineFuze.PROXIMITY_PLAYER && entity instanceof Player player
                    && player.isShiftKeyDown()
                    && distSq > SNEAK_TRIGGER_DIST_SQ) {
                continue;
            }
            this.detonate(info);
            return;
        }
    }

    private static boolean triggers(@Nullable Entity entity, WeaponSpec info) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        return switch (info.definition().mine().fuze()) {
            case PROXIMITY_PLAYER -> entity instanceof Player player && !player.isSpectator() && !player.isCreative();
            case PROXIMITY_VEHICLE -> entity instanceof Vehicle;
            default -> false;
        };
    }

    private void detonate(WeaponSpec info) {
        ProjectileEntity p = this.bomb;
        Warhead warhead = p.warhead();
        if (p.isInWater() && warhead.explosionPowerInWater() > 0) {
            warhead.explode(p, p.position(), warhead.explosionPowerInWater(), warhead.explosionPowerInWater(), true);
        } else if (warhead.explosionPower() > 0) {
            // explode() applies the new explosion rules; only the fallback block power differs here
            int power = warhead.explosionPower();
            float blockPower = info.explosion().newBreak() && info.explosionBlock() < 0 ? power : info.explosionBlock();
            warhead.explode(p, p.position(), power, power, power, blockPower, false);
        } else {
            Warhead.playExplosionSound(p);
        }
        warhead.playHitSound(p, p.position());
        p.discard();
    }

    /** A mine defuser removes the mine. */
    @Override
    @Nullable
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.isMine()) {
            return null;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !(held.getItem() instanceof MineDefuserItem)) {
            return null;
        }
        if (!this.bomb.level().isClientSide) {
            this.bomb.level().playSound(null, this.bomb.getX(), this.bomb.getY(), this.bomb.getZ(),
                    SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.6F);
            this.bomb.discard();
        }
        player.swing(hand);
        return InteractionResult.sidedSuccess(this.bomb.level().isClientSide);
    }

    /** A mine is laid to wait, as long as it takes: it is saved with the world and never expires. */
    @Override
    public boolean persists() {
        return this.isMine();
    }

    @Override
    public void save(CompoundTag tag) {
        if (this.isMine()) {
            tag.putBoolean("MchMine", true);
            tag.putBoolean("MineLanded", this.isLanded());
            tag.putBoolean("MineArmed", this.armed);
            tag.putInt("MineArmTimer", this.armTimer);
        }
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (!tag.getBoolean("MchMine")) {
            return false;
        }
        this.bomb.setWeapon(tag.getString("WeaponName"));
        this.armed = tag.getBoolean("MineArmed");
        this.armTimer = tag.getInt("MineArmTimer");
        this.setLanded(tag.getBoolean("MineLanded"));
        this.bomb.setDeltaMovement(Vec3.ZERO);
        return true;
    }
}
