package com.ivomens.mcheli.architecture;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The rules about vehicle components (docs/ARCHITECTURE_RULES.md, "Components"): a component gets its
 * collaborators through its constructor and never from the vehicle entity, the constructors form no
 * cycle in any vehicle kind, a component only orders its tick after components it depends on, and
 * docs/COMPONENTS.md shows the graph as it is.
 */
@AnalyzeClasses(locations = ArchitectureRulesTest.MainClasses.class)
class ComponentRulesTest {

    /** Where component code lives besides the classes that implement the interface: their helpers. */
    private static final String COMPONENT_PACKAGE = ComponentGraph.MOD + ".vehicle.component";
    private static final String VEHICLE_ENTITY = ComponentGraph.MOD + ".aircraft.VehicleEntity";
    /** The config: its stores, its typed sections and their helpers. */
    private static final String CONFIG = ComponentGraph.MOD + ".config";
    /** The one place that reads the config for the vehicle layer. */
    private static final String SETTINGS_HOLDER = ComponentGraph.MOD + ".vehicle.settings.VehicleSettingsHolder";
    /**
     * Vehicle code outside the components that gets its settings handed in like them: the physics
     * adapter, the assembly, the tick pipeline and its plan, and the pilot assists.
     */
    private static final List<String> SETTINGS_INJECTED = List.of(
            ComponentGraph.MOD + ".aircraft.AircraftPhysics", ComponentGraph.ASSEMBLY,
            ComponentGraph.MOD + ".aircraft.VehicleTickPipeline", ComponentGraph.MOD + ".aircraft.TickPlan");
    private static final List<String> SETTINGS_INJECTED_PACKAGES = List.of(
            ComponentGraph.MOD + ".core", ComponentGraph.MOD + ".aircraft.assist");

    /**
     * Components and their helpers call no method of a vehicle entity that hands out a component (or an
     * interface a component implements). What a component works with comes in through its
     * constructor; another vehicle's components are reached through {@code VehiclePeers}.
     */
    @ArchTest
    static void componentsGetTheirCollaboratorsThroughTheirConstructor(JavaClasses classes) {
        JavaClass vehicle = classes.get(ComponentGraph.VEHICLE);
        Set<String> componentFaces = componentFaces(classes);
        SortedMap<String, String> found = new TreeMap<>();
        for (JavaClass type : classes) {
            if (!isComponentCode(outermost(type))) {
                continue;
            }
            Stream.concat(type.getMethodCallsFromSelf().stream(), type.getMethodReferencesFromSelf().stream())
                    .filter(access -> isEntity(access.getTargetOwner(), vehicle))
                    .filter(access -> handsOutComponent(access.getTarget(), componentFaces))
                    .forEach(access -> found.putIfAbsent(Violations.topLevelName(type) + " "
                            + access.getTargetOwner().getSimpleName() + "." + access.getName() + "()",
                            access.getDescription()));
        }
        RuleCheck.noViolations("Components get their collaborators through their constructor, never from the "
                + "vehicle entity", found);
    }

    /**
     * Components, their helpers, the physics adapter, the assembly, the tick pipeline, the pilot
     * assists and {@code :core} read no config: they get the part of the settings snapshot they need
     * through their constructors (a {@code vehicle.settings} record), never {@code McheliConfig}, a
     * config section or the snapshot holder.
     */
    @ArchTest
    static void componentsGetTheirSettingsHandedIn(JavaClasses classes) {
        SortedMap<String, String> found = new TreeMap<>();
        for (JavaClass type : classes) {
            if (!getsSettingsHandedIn(outermost(type))) {
                continue;
            }
            for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target.getPackageName().startsWith(CONFIG) || target.getName().equals(SETTINGS_HOLDER)) {
                    found.putIfAbsent(Violations.topLevelName(type) + " " + target.getSimpleName(),
                            dependency.getDescription());
                }
            }
        }
        RuleCheck.noViolations("Components and the vehicle core get their settings handed in, they never read "
                + "the config", found);
    }

    /**
     * A component's constructor takes at most {@value Collaborators#LIMIT} collaborators (other
     * components and narrow ports; see {@link Collaborators} for what counts), so no component grows
     * into a hub that knows the whole vehicle.
     */
    @ArchTest
    static void componentsTakeFewCollaborators(JavaClasses classes) {
        SortedMap<String, Integer> found = new TreeMap<>();
        for (JavaClass component : ComponentGraph.allComponents(classes)) {
            int count = Collaborators.of(component).size();
            if (count > Collaborators.LIMIT) {
                found.put(component.getName() + " (" + String.join(", ", Collaborators.of(component)) + ")", count);
            }
        }
        RuleCheck.withinLimit("A component's constructor takes at most " + Collaborators.LIMIT + " collaborators",
                found, Collaborators.LIMIT, "collaborators");
    }

    /** The components of every vehicle kind can be built in some order: their constructors form no cycle. */
    @ArchTest
    static void componentDependenciesFormNoCycles(JavaClasses classes) {
        SortedMap<String, String> found = new TreeMap<>();
        SortedMap<String, ComponentGraph> kinds = ComponentGraph.byKind(classes);
        if (kinds.isEmpty()) {
            found.put("no vehicle kinds", "found no concrete subclass of " + ComponentGraph.VEHICLE);
        }
        kinds.forEach((kind, graph) -> graph.cycle()
                .ifPresent(cycle -> found.put(kind, String.join(" -> ", cycle))));
        RuleCheck.noViolations("The components of each vehicle kind depend on each other in no cycle", found);
    }

    /**
     * {@code @TickOrder(after = ...)} only names components the component depends on, so an order
     * constraint is always a visible dependency; and a component with a tick of its own declares
     * {@code @TickOrder}.
     */
    @ArchTest
    static void tickOrderFollowsTheDependencies(JavaClasses classes) {
        SortedMap<String, String> found = new TreeMap<>();
        for (JavaClass component : ComponentGraph.allComponents(classes)) {
            Set<JavaClass> parameters = ComponentGraph.constructorParameters(component);
            for (JavaClass after : ComponentGraph.afterClasses(component)) {
                if (parameters.stream().noneMatch(parameter -> after.isAssignableTo(parameter.getName()))) {
                    found.put(component.getName() + " after " + after.getSimpleName(),
                            "@TickOrder names " + after.getSimpleName() + ", which its constructor does not take");
                }
            }
            if (ticks(component) && ComponentGraph.phase(component).isEmpty()) {
                found.put(component.getName() + " ticks", "overrides serverTick or clientTick without @TickOrder");
            }
        }
        RuleCheck.noViolations("A component ticks after components it depends on only, and declares its phase",
                found);
    }

    /** docs/COMPONENTS.md is what {@link ComponentGraphDoc} makes of the classes. */
    @ArchTest
    static void componentsDocIsCurrent(JavaClasses classes) throws IOException {
        String committed = Files.isRegularFile(ComponentGraphDoc.path())
                ? Files.readString(ComponentGraphDoc.path(), StandardCharsets.UTF_8) : "";
        assertEquals(ComponentGraphDoc.generate(classes), committed,
                "docs/COMPONENTS.md is stale; run ./gradlew :architecture:writeComponentGraph and commit it");
    }

    private static boolean getsSettingsHandedIn(JavaClass type) {
        return isComponentCode(type) || SETTINGS_INJECTED.contains(type.getName())
                || SETTINGS_INJECTED_PACKAGES.stream().anyMatch(type.getPackageName()::startsWith);
    }

    private static boolean isComponentCode(JavaClass type) {
        return type.isAssignableTo(ComponentGraph.COMPONENT) || type.getPackageName().startsWith(COMPONENT_PACKAGE);
    }

    /** A vehicle entity class, or one of the mod's types it is seen through ({@code Vehicle}, {@code ArmedVehicle}). */
    private static boolean isEntity(JavaClass owner, JavaClass vehicle) {
        return owner.isAssignableTo(VEHICLE_ENTITY)
                || owner.getPackageName().startsWith(ComponentGraph.MOD) && vehicle.isAssignableTo(owner.getName());
    }

    private static boolean handsOutComponent(AccessTarget target, Set<String> componentFaces) {
        if (!(target instanceof AccessTarget.CodeUnitAccessTarget codeUnit)) {
            return false;
        }
        JavaClass returned = codeUnit.getRawReturnType();
        return returned.isAssignableTo(ComponentGraph.COMPONENT) || componentFaces.contains(returned.getName());
    }

    /** The mod's interfaces that a component implements, such as {@code Crew} for the seats. */
    private static Set<String> componentFaces(JavaClasses classes) {
        Set<String> faces = new HashSet<>();
        for (JavaClass component : ComponentGraph.allComponents(classes)) {
            component.getAllRawInterfaces().stream()
                    .filter(face -> face.getPackageName().startsWith(ComponentGraph.MOD))
                    .filter(face -> !face.getName().equals(ComponentGraph.COMPONENT))
                    .forEach(face -> faces.add(face.getName()));
        }
        return faces;
    }

    /** Whether the component declares its own server or client tick. */
    private static boolean ticks(JavaClass component) {
        return component.getMethods().stream()
                .filter(method -> List.of("serverTick", "clientTick").contains(method.getName()))
                .anyMatch(method -> method.getRawParameterTypes().size() == 1
                        && method.getRawParameterTypes().getFirst().getName().equals(ComponentGraph.TICK_CONTEXT));
    }

    private static JavaClass outermost(JavaClass type) {
        JavaClass outermost = type;
        while (outermost.getEnclosingClass().isPresent()) {
            outermost = outermost.getEnclosingClass().get();
        }
        return outermost;
    }
}
