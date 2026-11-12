package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.vehicle.extension.BehaviourContext;
import com.ivomens.mcheli.api.vehicle.extension.VehicleBehaviour;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A beacon on top of a vehicle: every {@code Interval} ticks it pulses, which clients draw as a column of light
 * {@code Height} blocks above the hull while the vehicle is intact. The server counts the pulses, to show the
 * behaviour ticks there too.
 *
 * <pre>
 * Behaviours:
 *   mcheli_example:beacon: { Interval: 10, Height: 3.0 }
 * </pre>
 */
public final class BeaconBehaviour implements VehicleBehaviour {

    /** Every pulse on the server since the game started, for the example's game test. */
    private static final AtomicLong SERVER_PULSES = new AtomicLong();

    /** What a vehicle file sets; every value has a default. */
    public record Settings(int interval, double height) {

        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 1200).optionalFieldOf("Interval", 10).forGetter(Settings::interval),
                Codec.DOUBLE.optionalFieldOf("Height", 3.0).forGetter(Settings::height))
                .apply(instance, Settings::new));

        static Settings decode(CompoundTag tag) {
            return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(IllegalArgumentException::new);
        }
    }

    private final Settings settings;
    private int ticks;
    private boolean wrecked;

    public BeaconBehaviour(Settings settings, BehaviourContext context) {
        this.settings = settings;
    }

    /** The pulses counted on the server since the game started. */
    public static long serverPulses() {
        return SERVER_PULSES.get();
    }

    @Override
    public void serverTick(BehaviourContext context) {
        if (pulses()) {
            SERVER_PULSES.incrementAndGet();
        }
    }

    @Override
    public void clientTick(BehaviourContext context) {
        if (!pulses()) {
            return;
        }
        Entity hull = context.vehicle().entity();
        for (int i = 0; i < 8; i++) {
            double y = hull.getBbHeight() + this.settings.height() * i / 8;
            context.level().addParticle(ParticleTypes.END_ROD, hull.getX(), hull.getY() + y, hull.getZ(), 0.0, 0.02,
                    0.0);
        }
    }

    /** A wrecked vehicle's beacon goes dark. */
    @Override
    public void destroyed(BehaviourContext context) {
        this.wrecked = true;
    }

    private boolean pulses() {
        return !this.wrecked && ++this.ticks % this.settings.interval() == 0;
    }
}
