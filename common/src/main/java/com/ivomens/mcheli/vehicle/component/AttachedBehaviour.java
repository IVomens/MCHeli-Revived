package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.extension.BehaviourContext;
import com.ivomens.mcheli.api.vehicle.extension.BehaviourTiming;
import com.ivomens.mcheli.api.vehicle.extension.VehicleBehaviour;
import com.ivomens.mcheli.api.vehicle.extension.VehicleControls;
import com.ivomens.mcheli.extension.ConfiguredBehaviour;
import com.ivomens.mcheli.util.ModLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * One behaviour of another mod on one vehicle, with the context its hooks get. A hook that throws is logged and the
 * behaviour is dropped from the vehicle, so a broken addon cannot stop the vehicle from ticking.
 */
final class AttachedBehaviour implements BehaviourContext {

    private final ResourceLocation id;
    private final BehaviourTiming timing;
    private final Vehicle vehicle;
    private final VehicleControls controls;
    private VehicleBehaviour behaviour;
    private boolean sawWreck;
    private boolean failed;

    private AttachedBehaviour(ConfiguredBehaviour<?> configured, Vehicle vehicle, VehicleControls controls) {
        this.id = configured.id();
        this.timing = configured.type().timing();
        this.vehicle = vehicle;
        this.controls = controls;
    }

    /** Makes the behaviour and tells it it was added; null when its factory or {@code added} threw. */
    static AttachedBehaviour attach(ConfiguredBehaviour<?> configured, Vehicle vehicle, VehicleControls controls) {
        AttachedBehaviour attached = new AttachedBehaviour(configured, vehicle, controls);
        try {
            attached.behaviour = configured.create(attached);
        } catch (RuntimeException e) {
            ModLogger.error("Behaviour " + attached.id + " could not be made for " + vehicle.entity(), e);
            return null;
        }
        attached.call("added", attached.behaviour::added);
        return attached.failed ? null : attached;
    }

    BehaviourTiming timing() {
        return this.timing;
    }

    boolean failed() {
        return this.failed;
    }

    void tick() {
        if (this.vehicle.isDestroyed() && !this.sawWreck) {
            this.sawWreck = true;
            call("destroyed", this.behaviour::destroyed);
        }
        if (isClientSide()) {
            call("clientTick", this.behaviour::clientTick);
        } else {
            call("serverTick", this.behaviour::serverTick);
        }
    }

    void detach() {
        call("removed", this.behaviour::removed);
    }

    private void call(String hook, Consumer<BehaviourContext> action) {
        if (this.failed) {
            return;
        }
        try {
            action.accept(this);
        } catch (RuntimeException e) {
            this.failed = true;
            ModLogger.error("Behaviour " + this.id + " threw in " + hook + " on " + this.vehicle.entity()
                    + "; it is removed from the vehicle", e);
        }
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public Vehicle vehicle() {
        return this.vehicle;
    }

    @Override
    public Level level() {
        return this.vehicle.entity().level();
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide();
    }

    @Override
    public VehicleControls controls() {
        return this.controls;
    }
}
