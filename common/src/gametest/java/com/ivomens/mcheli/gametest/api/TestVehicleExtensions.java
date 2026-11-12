package com.ivomens.mcheli.gametest.api;

import com.ivomens.mcheli.api.vehicle.extension.BehaviourContext;
import com.ivomens.mcheli.api.vehicle.extension.BehaviourTiming;
import com.ivomens.mcheli.api.vehicle.extension.MovementContext;
import com.ivomens.mcheli.api.vehicle.extension.MovementModel;
import com.ivomens.mcheli.api.vehicle.extension.VehicleBehaviour;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionProvider;
import com.ivomens.mcheli.api.vehicle.extension.VehicleExtensionRegistrar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the game tests' own behaviours and movement model through the public API, as an addon would; the
 * {@code api_test_hover} vehicle of the test pack uses them, and {@link ExtensionGameTests} checks what they did.
 */
public final class TestVehicleExtensions implements VehicleExtensionProvider {

    static final ResourceLocation HOVER = ResourceLocation.fromNamespaceAndPath("mcheli_test", "hover");
    static final ResourceLocation PROBE_EARLY = ResourceLocation.fromNamespaceAndPath("mcheli_test", "probe_early");
    static final ResourceLocation PROBE_LATE = ResourceLocation.fromNamespaceAndPath("mcheli_test", "probe_late");
    /** Takes a whole number {@code Required}; rejects anything else. */
    static final ResourceLocation STRICT = ResourceLocation.fromNamespaceAndPath("mcheli_test", "strict");

    /** What the probes saw, per vehicle entity. */
    static final Map<Entity, Probe> PROBES = new ConcurrentHashMap<>();

    @Override
    public void register(VehicleExtensionRegistrar registrar) {
        // the benchmarks measure the game as players without addons run it: no behaviour steps in the tick
        if (!System.getProperty("mcheli.bench", "").isEmpty()) {
            return;
        }
        registrar.movementModel(HOVER, HoverSettings::of, Hover::new);
        registrar.behaviour(PROBE_EARLY, BehaviourTiming.BEFORE_MOVEMENT, tag -> tag,
                (settings, context) -> new EarlyProbe());
        registrar.behaviour(PROBE_LATE, BehaviourTiming.AFTER_MOVEMENT, tag -> tag.getString("Label"),
                (label, context) -> new LateProbe(label));
        registrar.behaviour(STRICT, BehaviourTiming.AFTER_MOVEMENT, tag -> {
            if (!tag.contains("Required", CompoundTag.TAG_INT)) {
                throw new IllegalArgumentException("Required must be a whole number");
            }
            return tag.getInt("Required");
        }, (settings, context) -> new VehicleBehaviour() {
        });
    }

    static Probe probe(BehaviourContext context) {
        return PROBES.computeIfAbsent(context.vehicle().entity(), entity -> new Probe());
    }

    /** What both probes of one vehicle saw. */
    static final class Probe {
        int earlyAdded;
        int lateAdded;
        int earlyTicks;
        int lateTicks;
        int earlyRemoved;
        int lateRemoved;
        boolean sawForward;
        String label;
        Vec3 before;
        /** How far the vehicle moved between the early and the late probe of the last tick. */
        Vec3 moved = Vec3.ZERO;
    }

    private static final class EarlyProbe implements VehicleBehaviour {

        @Override
        public void added(BehaviourContext context) {
            probe(context).earlyAdded++;
        }

        @Override
        public void serverTick(BehaviourContext context) {
            Probe probe = probe(context);
            probe.earlyTicks++;
            probe.before = context.vehicle().entity().position();
            probe.sawForward |= context.controls().forward();
        }

        @Override
        public void removed(BehaviourContext context) {
            probe(context).earlyRemoved++;
        }
    }

    private record LateProbe(String label) implements VehicleBehaviour {

        @Override
        public void added(BehaviourContext context) {
            Probe probe = probe(context);
            probe.lateAdded++;
            probe.label = this.label;
        }

        @Override
        public void serverTick(BehaviourContext context) {
            Probe probe = probe(context);
            probe.lateTicks++;
            if (probe.before != null) {
                probe.moved = context.vehicle().entity().position().subtract(probe.before);
            }
        }

        @Override
        public void removed(BehaviourContext context) {
            probe(context).lateRemoved++;
        }
    }

    record HoverSettings(double hoverHeight, double speed, float turnRate) {

        static HoverSettings of(CompoundTag tag) {
            return new HoverSettings(tag.getDouble("HoverHeight"), tag.getDouble("Speed"), tag.getFloat("TurnRate"));
        }
    }

    /**
     * Holds the hull {@code HoverHeight} above the ground, flies {@code Speed} blocks per tick straight ahead while
     * the forward key is held and stops dead when it is not, and turns {@code TurnRate} degrees per tick with the
     * side keys.
     */
    private record Hover(HoverSettings settings) implements MovementModel {

        @Override
        public void step(MovementContext context) {
            double height = context.heightAboveGround(8);
            double climb = Double.isInfinite(height) ? -0.2
                    : Math.max(-0.2, Math.min(0.2, (this.settings.hoverHeight() - height) * 0.5));
            float yaw = context.yaw();
            if (context.controls().left()) {
                yaw -= this.settings.turnRate();
            } else if (context.controls().right()) {
                yaw += this.settings.turnRate();
            }
            context.setYaw(yaw);
            double speed = context.controls().forward() ? this.settings.speed() : 0.0;
            double radians = Math.toRadians(yaw);
            context.setVelocity(new Vec3(-Math.sin(radians) * speed, climb, Math.cos(radians) * speed));
        }
    }
}
