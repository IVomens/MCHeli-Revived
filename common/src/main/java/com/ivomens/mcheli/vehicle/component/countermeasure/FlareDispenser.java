package com.ivomens.mcheli.vehicle.component.countermeasure;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.vehicle.component.BodyComponent;
import com.ivomens.mcheli.combat.countermeasure.CountermeasureFlag;
import com.ivomens.mcheli.combat.countermeasure.FlareEntity;
import com.ivomens.mcheli.combat.countermeasure.FlarePattern;
import com.ivomens.mcheli.platform.LocalClient;
import com.ivomens.mcheli.util.JavaLcgRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The flare dispenser of a vehicle: releases salvos in a {@link FlarePattern} behind the vehicle. While
 * the first part of the cooldown runs, heat seekers tracking the vehicle can be decoyed.
 */
public final class FlareDispenser implements Countermeasure {

    private final Level level;
    private final AircraftEntity vehicle;
    private final BodyComponent body;
    private final boolean client;
    /** The flares' own spread; kept apart from the level's generator so salvos replay. */
    private final RandomSource random = JavaLcgRandom.unseeded();
    @Nullable
    private FlarePattern pattern;
    private int cooldown;
    private int salvos;

    public FlareDispenser(AircraftEntity vehicle, BodyComponent body) {
        this.level = vehicle.level();
        this.vehicle = vehicle;
        this.body = body;
        this.client = this.level.isClientSide;
    }

    /** Releases flares in the pattern with the given legacy number; false if that is no pattern or not ready. */
    public boolean release(int legacyNumber) {
        FlarePattern chosen = FlarePattern.ofLegacyNumber(legacyNumber);
        if (chosen == null) {
            return false;
        }
        this.pattern = chosen;
        if (this.client) {
            if (this.cooldown != 0) {
                return false;
            }
            LocalClient.get().playClick();
        } else {
            CountermeasureFlag.FLARE_USING.set(this.vehicle, true);
        }
        this.cooldown = chosen.cooldown(this.client);
        this.salvos = 0;
        return true;
    }

    @Override
    public void tick() {
        FlarePattern current = this.pattern;
        if (this.vehicle.isRemoved() || current == null) {
            return;
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        if (!this.client && this.cooldown > 0 && this.cooldown % current.interval() == 0
                && this.salvos < current.maxSalvos() && this.vehicle.spec() != null
                && this.vehicle.spec().armament().flare() != null) {
            Vec3 pos = this.vehicle.spec().armament().flare().pos;
            this.releaseSalvo(current, this.body.toWorld(pos.x, pos.y, pos.z, this.vehicle.xo,
                    this.vehicle.yo, this.vehicle.zo));
        }
        if (!this.isActive()) {
            CountermeasureFlag.FLARE_USING.clear(this.vehicle);
        }
    }

    @Override
    public boolean isActive() {
        return this.pattern != null && this.pattern.isDecoying(this.cooldown, this.client);
    }

    @Override
    public boolean isRecharging() {
        return this.cooldown != 0;
    }

    public int cooldown() {
        return this.cooldown;
    }

    RandomSource random() {
        return this.random;
    }

    private void releaseSalvo(FlarePattern current, Vec3 dispenser) {
        this.salvos++;
        Vec3 motion = this.vehicle.getDeltaMovement();
        Vec3 base = new Vec3(dispenser.x - motion.x * 2.0, dispenser.y - motion.y * 2.0 - 1.0, dispenser.z - motion.z * 2.0);
        this.level.playSound(null, BlockPos.containing(base), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.6F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.8F);
        int count = current.salvoSize();
        for (int i = 0; i < count; i++) {
            this.releaseFlare(current, i, count, base, motion);
        }
    }

    private void releaseFlare(FlarePattern current, int index, int count, Vec3 base, Vec3 vehicleMotion) {
        double y = base.y;
        int fuse = 0;
        Vec3 throwDir = this.throwDirection(current, index);
        double tx = throwDir.x + vehicleMotion.x;
        double ty = vehicleMotion.y + vehicleMotion.y / 2.0;
        double tz = throwDir.z + vehicleMotion.z;
        if (current == FlarePattern.RING) {
            // evenly spaced around the vehicle, popping after a short fuse
            double yaw = Math.toRadians(this.vehicle.getYRot() + 360.0 / count / 2 + index * (360.0 / count));
            tx = -Math.sin(yaw) * 2.0;
            tz = Math.cos(yaw) * 2.0;
            ty = 0.7;
            y += 2.0;
            fuse = 10;
        }
        FlareEntity flare = new FlareEntity(this.level, base.x, y, base.z, tx * 0.5, ty * 0.5, tz * 0.5, 6.0F, fuse);
        flare.setXRot(this.random.nextFloat() * 360.0F);
        flare.setYRot(this.random.nextFloat() * 360.0F);
        flare.xRotO = this.random.nextFloat() * 360.0F;
        flare.yRotO = this.random.nextFloat() * 360.0F;
        if (current == FlarePattern.FORWARD_WIDE) {
            flare.setGravity(flare.gravity() * 0.6);
            flare.setAirResistance(0.995);
        }
        this.level.addFreshEntity(flare);
    }

    /** The horizontal throw of one flare of the salvo, before the vehicle's motion is added. */
    private Vec3 throwDirection(FlarePattern current, int index) {
        double yaw = this.vehicle.getYRot();
        return switch (current) {
            // radians taken as degrees: effectively a random direction
            case RANDOM -> new Vec3(Mth.sin(this.random.nextFloat() * 360.0F), 0.0,
                    Mth.cos(this.random.nextFloat() * 360.0F));
            case SIDEWAYS, SIDEWAYS_PAIRS -> {
                double side = Math.toRadians(yaw + (index == 0 ? 90.0 : index == 1 ? -90.0 : index == 2 ? 180.0 : 0.0));
                double x = -Math.sin(side) + (this.random.nextFloat() - 0.5) * 0.6;
                yield new Vec3(x, 0.0, Math.cos(side) + (this.random.nextFloat() - 0.5) * 0.6);
            }
            case FORWARD_WIDE -> {
                double x = -Math.sin(Math.toRadians(yaw)) + (this.random.nextFloat() - 0.5) * 1.3;
                yield new Vec3(x, 0.0, Math.cos(Math.toRadians(yaw)) + (this.random.nextFloat() - 0.5) * 1.3);
            }
            case FORWARD_NARROW -> {
                double x = (-Math.sin(Math.toRadians(yaw)) + (this.random.nextFloat() - 0.5) * 0.9) * 0.3;
                yield new Vec3(x, 0.0, (Math.cos(Math.toRadians(yaw)) + (this.random.nextFloat() - 0.5) * 0.9) * 0.3);
            }
            case TRAILING, RING -> Vec3.ZERO;
        };
    }
}
