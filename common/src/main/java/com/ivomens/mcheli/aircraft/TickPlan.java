package com.ivomens.mcheli.aircraft;

import com.ivomens.mcheli.vehicle.component.ScheduledStep;
import com.ivomens.mcheli.vehicle.component.TickOrder;
import com.ivomens.mcheli.vehicle.component.TickPhase;
import com.ivomens.mcheli.vehicle.component.TickStep;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The steps of a vehicle tick, each in its {@link TickPhase}, and the check that their order keeps what
 * every component declares with {@link TickOrder}: its own step sits in its phase, after the steps of
 * the components it names. A broken declaration stops the vehicle from being built rather than letting
 * it tick in an order nobody chose.
 */
final class TickPlan {

    private final List<TickStep> steps = new ArrayList<>();
    private final List<TickPhase> phases = new ArrayList<>();

    /** Appends steps of a phase; phases come in their order and never interleave. */
    TickPlan then(TickPhase phase, TickStep... phaseSteps) {
        if (!this.phases.isEmpty() && phase.compareTo(this.phases.getLast()) < 0) {
            throw new IllegalStateException("Vehicle tick phase " + phase + " comes after " + this.phases.getLast());
        }
        for (TickStep step : phaseSteps) {
            this.steps.add(step);
            this.phases.add(phase);
        }
        return this;
    }

    List<TickStep> steps() {
        return List.copyOf(this.steps);
    }

    /** The steps in order with their phases, for the vehicle inspector. */
    List<ScheduledStep> schedule() {
        List<ScheduledStep> schedule = new ArrayList<>(this.steps.size());
        for (int i = 0; i < this.steps.size(); i++) {
            TickStep step = this.steps.get(i);
            schedule.add(new ScheduledStep(step.name(), this.phases.get(i), step.owner()));
        }
        return List.copyOf(schedule);
    }

    /** This plan with {@code placeholder} replaced by {@code inserted}, which run in the placeholder's phase. */
    TickPlan expand(TickStep placeholder, List<TickStep> inserted) {
        TickPlan expanded = new TickPlan();
        for (int i = 0; i < this.steps.size(); i++) {
            TickStep step = this.steps.get(i);
            List<TickStep> replacement = step == placeholder ? inserted : List.of(step);
            for (TickStep added : replacement) {
                expanded.steps.add(added);
                expanded.phases.add(this.phases.get(i));
            }
        }
        return expanded;
    }

    /**
     * Fails when the order breaks a component's {@link TickOrder}.
     *
     * @param complete whether every step of the tick is in the plan; until the vehicle type's steps are
     *                 known, a component named in {@code after} may still be missing
     */
    void check(boolean complete) {
        List<String> broken = new ArrayList<>();
        for (int i = 0; i < this.steps.size(); i++) {
            VehicleComponent owner = this.steps.get(i).owner();
            if (owner != null) {
                checkStep(i, owner, complete, broken);
            }
        }
        if (!broken.isEmpty()) {
            throw new IllegalStateException("Vehicle tick order broken:\n  " + String.join("\n  ", broken));
        }
    }

    private void checkStep(int index, VehicleComponent owner, boolean complete, List<String> broken) {
        String name = owner.getClass().getSimpleName();
        TickOrder order = owner.getClass().getAnnotation(TickOrder.class);
        if (order == null) {
            broken.add(name + " ticks in \"" + this.steps.get(index).name() + "\" but declares no @TickOrder");
            return;
        }
        if (order.phase() != this.phases.get(index)) {
            broken.add(name + " belongs in " + order.phase() + " but ticks in " + this.phases.get(index));
        }
        for (Class<? extends VehicleComponent> before : order.after()) {
            int beforeIndex = firstStepOf(before);
            if (beforeIndex < 0 && complete) {
                broken.add(name + " ticks after " + before.getSimpleName() + ", which does not tick");
            } else if (beforeIndex > index) {
                broken.add(name + " ticks after " + before.getSimpleName() + ", but runs before it");
            }
        }
    }

    private int firstStepOf(Class<? extends VehicleComponent> type) {
        for (int i = 0; i < this.steps.size(); i++) {
            if (type.isInstance(this.steps.get(i).owner())) {
                return i;
            }
        }
        return -1;
    }
}
