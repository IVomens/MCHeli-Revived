package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.api.vehicle.extension.BehaviourTiming;
import com.ivomens.mcheli.extension.ConfiguredBehaviour;
import com.ivomens.mcheli.extension.ExtensionRegistry;
import com.ivomens.mcheli.extension.VehicleAddons;

import java.util.ArrayList;
import java.util.List;

/**
 * The behaviours other mods add to the vehicle, as its file names them ({@code Behaviours}). They are made on the
 * vehicle's first tick that knows its type, made again when a content reload replaced the file, and told when the
 * vehicle is wrecked and when it leaves the level. {@link BehavioursBeforeMovement} and
 * {@link BehavioursAfterMovement} tick them, and only join the vehicle's tick while some mod registers a
 * behaviour type at all.
 */
public final class AddonBehaviours implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final KeyControls controls;
    /** What the attached behaviours were made from; compared by identity, a reload makes a new one. */
    private VehicleAddons source = VehicleAddons.NONE;
    private List<AttachedBehaviour> attached = List.of();

    public AddonBehaviours(AircraftEntity vehicle, LifecycleComponent lifecycle, ControlKeysComponent keys) {
        this.vehicle = vehicle;
        this.controls = new KeyControls(keys, vehicle);
        if (ExtensionRegistry.get().hasBehaviours()) {
            lifecycle.whenRemoved(reason -> detachAll());
        }
    }

    /** Ticks the behaviours of one timing, after catching up with the vehicle's type. */
    void tick(BehaviourTiming timing) {
        refresh();
        List<AttachedBehaviour> behaviours = this.attached;
        boolean failures = false;
        for (int i = 0; i < behaviours.size(); i++) {
            AttachedBehaviour behaviour = behaviours.get(i);
            if (behaviour.timing() == timing) {
                behaviour.tick();
                failures |= behaviour.failed();
            }
        }
        if (failures) {
            this.attached = behaviours.stream().filter(behaviour -> !behaviour.failed()).toList();
        }
    }

    private void refresh() {
        VehicleSpec spec = this.vehicle.spec();
        VehicleAddons wanted = spec != null ? spec.file().addons() : VehicleAddons.NONE;
        if (wanted == this.source || this.vehicle.isRemoved()) {
            return;
        }
        detachAll();
        this.source = wanted;
        List<AttachedBehaviour> made = new ArrayList<>(wanted.behaviours().size());
        for (ConfiguredBehaviour<?> configured : wanted.behaviours()) {
            AttachedBehaviour behaviour = AttachedBehaviour.attach(configured, this.vehicle, this.controls);
            if (behaviour != null) {
                made.add(behaviour);
            }
        }
        this.attached = List.copyOf(made);
    }

    private void detachAll() {
        List<AttachedBehaviour> leaving = this.attached;
        this.attached = List.of();
        this.source = VehicleAddons.NONE;
        for (AttachedBehaviour behaviour : leaving) {
            behaviour.detach();
        }
    }

    @Override
    public void describe(StateSink state) {
        state.put("behaviours", this.attached.stream().map(behaviour -> behaviour.id().toString()).toList()
                .toString());
    }
}
