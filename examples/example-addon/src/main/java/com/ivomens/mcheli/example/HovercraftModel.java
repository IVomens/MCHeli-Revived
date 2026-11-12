package com.ivomens.mcheli.example;

import com.ivomens.mcheli.api.vehicle.extension.MovementContext;
import com.ivomens.mcheli.api.vehicle.extension.MovementModel;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.phys.Vec3;

/**
 * A hovercraft: a cushion of air holds the hull {@code HoverHeight} blocks above the ground and lifts it out of
 * water it dips into, the forward and back keys push it along its heading, the side keys turn it, and it glides on
 * with little friction. Over a drop deeper than the cushion reaches it falls with the vehicle file's gravity.
 *
 * <pre>
 * Movement:
 *   Model: mcheli_example:hovercraft
 *   Settings: { HoverHeight: 1.0, Thrust: 0.02, TurnRate: 4.0, Drag: 0.96 }
 * </pre>
 */
public final class HovercraftModel implements MovementModel {

    /** How far down the cushion reaches, in blocks. */
    private static final int CUSHION_REACH = 4;
    /** How much of the height error the cushion corrects per tick. */
    private static final double CUSHION_STIFFNESS = 0.3;
    private static final double MAX_CLIMB = 0.25;

    /** What a vehicle file sets; every value has a default. */
    public record Settings(double hoverHeight, double thrust, float turnRate, double drag) {

        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("HoverHeight", 1.0).forGetter(Settings::hoverHeight),
                Codec.DOUBLE.optionalFieldOf("Thrust", 0.02).forGetter(Settings::thrust),
                Codec.FLOAT.optionalFieldOf("TurnRate", 4.0F).forGetter(Settings::turnRate),
                Codec.doubleRange(0.0, 1.0).optionalFieldOf("Drag", 0.96).forGetter(Settings::drag))
                .apply(instance, Settings::new));

        /** The settings of a vehicle file; throws with the codec's message when they are not valid. */
        static Settings decode(CompoundTag tag) {
            return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(IllegalArgumentException::new);
        }
    }

    private final Settings settings;

    public HovercraftModel(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void step(MovementContext context) {
        float yaw = context.yaw();
        if (context.controls().left()) {
            yaw -= this.settings.turnRate();
        } else if (context.controls().right()) {
            yaw += this.settings.turnRate();
        }
        context.setYaw(yaw);

        double push = context.controls().forward() ? this.settings.thrust()
                : context.controls().back() ? -this.settings.thrust() / 2 : 0.0;
        double radians = Math.toRadians(yaw);
        Vec3 velocity = context.velocity().add(-Math.sin(radians) * push, 0.0, Math.cos(radians) * push);
        context.setVelocity(new Vec3(velocity.x, verticalSpeed(context), velocity.z));
    }

    /** The cushion pushes the hull towards its height (out of water first); beyond its reach the hull falls. */
    private double verticalSpeed(MovementContext context) {
        double height = context.inWater() ? -context.waterDepth() : context.heightAboveGround(CUSHION_REACH);
        if (Double.isInfinite(height)) {
            return context.velocity().y + context.gravity();
        }
        double climb = (this.settings.hoverHeight() - height) * CUSHION_STIFFNESS;
        return Math.max(-MAX_CLIMB, Math.min(MAX_CLIMB, climb));
    }

    /** Little friction: the hull glides on, slowing by {@code Drag} per tick. */
    @Override
    public void afterMove(MovementContext context) {
        Vec3 velocity = context.velocity();
        context.setVelocity(new Vec3(velocity.x * this.settings.drag(), velocity.y, velocity.z * this.settings.drag()));
    }

    /** Clients predict the same glide between server updates. */
    @Override
    public void predict(MovementContext context) {
        afterMove(context);
    }
}
