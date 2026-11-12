package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.pack.AddonManager;
import com.ivomens.mcheli.content.pack.AddonPack;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.inspector.SnapshotBuilder.SectionWriter;
import com.ivomens.mcheli.vehicle.component.ScheduledStep;
import com.ivomens.mcheli.vehicle.component.StateSink;
import com.ivomens.mcheli.vehicle.component.TickPhase;
import com.ivomens.mcheli.vehicle.component.VehicleComponent;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Takes a snapshot of a vehicle as this side sees it: the overview, every component in tick order, the
 * weapons, the crew and the definition. The same code runs on the client (for its own view) and on the
 * server (for an operator's request), so the two snapshots can be compared key by key.
 */
public final class VehicleSnapshots {

    /** The overview's sections, by title. */
    public static final String IDENTITY = "Identity";
    public static final String STATE = "State";
    public static final String MOTION = "Motion";
    public static final String TICK = "Tick";

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MPS_TO_KMH = 3.6;

    private VehicleSnapshots() {
    }

    /** A snapshot of {@code vehicle}; see {@link DefinitionSections} for what {@code definition} shows. */
    public static InspectorSnapshot capture(AircraftEntity vehicle, DefinitionSections.Detail definition) {
        VehicleSpec spec = vehicle.spec();
        SnapshotBuilder builder = new SnapshotBuilder(vehicle.getId(), typeId(spec), vehicle.level().getGameTime());
        overview(builder, vehicle, spec);
        components(builder, vehicle);
        CrewSections.weapons(builder, vehicle);
        CrewSections.seats(builder, vehicle);
        if (spec != null) {
            DefinitionSections.write(builder, spec, definition);
        }
        return builder.build();
    }

    /** Only the definition tab, every value with its file position; it changes only when the type does. */
    public static InspectorSnapshot definition(AircraftEntity vehicle) {
        VehicleSpec spec = vehicle.spec();
        SnapshotBuilder builder = new SnapshotBuilder(vehicle.getId(), typeId(spec), vehicle.level().getGameTime());
        if (spec != null) {
            DefinitionSections.write(builder, spec, DefinitionSections.Detail.ALL_WITH_SOURCES);
        }
        return builder.build();
    }

    /** {@code <kind>/<name>}, or {@code unknown} before the type is loaded. */
    public static String typeId(VehicleSpec spec) {
        return spec == null ? "unknown" : spec.kindName() + "/" + spec.name();
    }

    private static void overview(SnapshotBuilder builder, AircraftEntity v, VehicleSpec spec) {
        identity(builder.section(InspectorTab.OVERVIEW, IDENTITY, ""), v, spec);
        SectionWriter state = builder.section(InspectorTab.OVERVIEW, STATE, "");
        state.put("health", v.damage().hp() + " / " + v.damage().maxHp());
        state.put("fuel", v.fuel().amount() + " / " + v.fuel().capacity());
        state.put("throttle", v.engine().throttle());
        state.put("synced throttle", v.engine().syncedThrottle());
        state.put("destroyed", v.isDestroyed());
        state.put("rest", v.rest().status());
        state.put("age", v.lifecycle().age());
        motion(builder.section(InspectorTab.OVERVIEW, MOTION, "from the sim body"), v);
        tick(builder.section(InspectorTab.OVERVIEW, TICK, ""), v);
    }

    private static void identity(SectionWriter out, AircraftEntity v, VehicleSpec spec) {
        out.put("type", typeId(spec));
        out.put("name", v.getName().getString());
        out.put("kind", spec == null ? "-" : spec.kindName());
        Optional<ContentEntry<VehicleFile>> entry = spec == null ? Optional.empty() : VehicleTypes.entry(spec);
        out.put("pack", entry.map(VehicleSnapshots::packName).orElse("-"));
        out.put("file", entry.map(ContentEntry::filePath).orElse("-"));
        out.put("format", entry.map(ContentEntry::format).orElse("-"));
        out.put("author", entry.map(ContentEntry::author).filter(author -> !author.isBlank()).orElse("-"));
        out.put("owner", owner(v));
        out.put("entity id", v.getId());
        out.put("uuid", v.getUUID().toString());
    }

    private static String packName(ContentEntry<VehicleFile> entry) {
        String domain = entry.location().getAddonDomain();
        AddonPack pack = AddonManager.get(domain);
        return pack == null || pack.getName().equals(domain) ? domain : pack.getName() + " (" + domain + ")";
    }

    /** The owner, as the first component that tracks one describes it; "-" while nothing does. */
    private static String owner(AircraftEntity v) {
        Map<String, String> found = new LinkedHashMap<>();
        for (VehicleComponent component : ComponentCatalog.of(v).keySet()) {
            component.describe((key, value) -> {
                if (key.equals("owner") && !found.containsKey(key)) {
                    found.put(key, value);
                }
            });
        }
        return found.getOrDefault("owner", "-");
    }

    private static void motion(SectionWriter out, AircraftEntity v) {
        Vec3 velocity = v.getDeltaMovement();
        Attitude attitude = v.body().attitude();
        out.put("position", vector(v.position()));
        out.put("velocity (m/tick)", vector(velocity));
        out.put("speed (km/h)", fixed(velocity.length() * TICKS_PER_SECOND * MPS_TO_KMH, 1));
        out.put("yaw", fixed(attitude.yaw(), 2));
        out.put("pitch", fixed(attitude.pitch(), 2));
        out.put("roll", fixed(attitude.roll(), 2));
        out.put("on ground", v.onGround());
    }

    private static void tick(SectionWriter out, AircraftEntity v) {
        List<ScheduledStep> schedule = v.tickSchedule();
        Map<TickPhase, Integer> perPhase = new EnumMap<>(TickPhase.class);
        schedule.forEach(step -> perPhase.merge(step.phase(), 1, Integer::sum));
        out.put("steps", schedule.size());
        perPhase.forEach((phase, count) -> out.put(phase.name().toLowerCase(Locale.ROOT), count + " steps"));
        out.put("phase timings", "not measured");
    }

    /** Every component, ticking ones first in tick order (with their phase and step), then the others. */
    private static void components(SnapshotBuilder builder, AircraftEntity v) {
        List<ScheduledStep> schedule = v.tickSchedule();
        Map<VehicleComponent, String> ticking = new LinkedHashMap<>();
        for (int i = 0; i < schedule.size(); i++) {
            ScheduledStep step = schedule.get(i);
            if (step.owner() != null && !ticking.containsKey(step.owner())) {
                ticking.put(step.owner(), step.phase() + "  step " + (i + 1) + ": " + step.name());
            }
        }
        ticking.forEach((component, detail) -> component(builder, component, detail));
        ComponentCatalog.of(v).forEach((component, name) -> {
            if (!ticking.containsKey(component)) {
                component(builder, component, "no tick of its own");
            }
        });
    }

    private static void component(SnapshotBuilder builder, VehicleComponent component, String detail) {
        component.describe(builder.section(InspectorTab.COMPONENTS, component.getClass().getSimpleName(), detail));
    }

    static String vector(Vec3 vector) {
        return fixed(vector.x, 3) + ", " + fixed(vector.y, 3) + ", " + fixed(vector.z, 3);
    }

    static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** Whole numbers as they are, others with three decimals. */
    static String number(double value) {
        return StateSink.number(value);
    }
}
