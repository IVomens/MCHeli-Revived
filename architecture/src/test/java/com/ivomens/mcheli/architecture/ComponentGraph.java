package com.ivomens.mcheli.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaModifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The vehicle components and what each depends on, read from the compiled classes: the components a
 * vehicle is made of are the ones its assembly ({@code AircraftComponents}) and its entity classes
 * construct, and a component depends on every other component its constructor takes.
 *
 * <p>Built once per vehicle kind (each concrete subclass of {@code AircraftEntity}). The components
 * every vehicle shares are the {@linkplain #shared shared} part; each kind adds its own.
 */
final class ComponentGraph {

    static final String MOD = "com.ivomens.mcheli";
    /** The interface every component implements. */
    static final String COMPONENT = MOD + ".vehicle.component.VehicleComponent";
    static final String TICK_ORDER = MOD + ".vehicle.component.TickOrder";
    static final String TICK_CONTEXT = MOD + ".vehicle.component.TickContext";
    /** Wires the components every vehicle shares. */
    static final String ASSEMBLY = MOD + ".aircraft.AircraftComponents";
    /** The base class of every vehicle kind. */
    static final String VEHICLE = MOD + ".aircraft.AircraftEntity";

    /** One component: the components it depends on, and where it ticks if it declares it. */
    record Node(JavaClass type, SortedSet<String> dependsOn, String phase, List<String> after) {

        String name() {
            return this.type.getSimpleName();
        }
    }

    private final String kind;
    private final SortedMap<String, Node> nodes;

    private ComponentGraph(String kind, SortedMap<String, Node> nodes) {
        this.kind = kind;
        this.nodes = nodes;
    }

    /** One graph per vehicle kind, by the kind's name ("Helicopter", "Ground vehicle", ...). */
    static SortedMap<String, ComponentGraph> byKind(JavaClasses classes) {
        JavaClass vehicle = classes.get(VEHICLE);
        JavaClass assembly = classes.get(ASSEMBLY);
        SortedMap<String, ComponentGraph> kinds = new TreeMap<>();
        for (JavaClass type : vehicle.getAllSubclasses()) {
            if (type.getModifiers().contains(JavaModifier.ABSTRACT)) {
                continue;
            }
            List<JavaClass> builders = new ArrayList<>();
            builders.add(assembly);
            for (JavaClass step = type; !step.equals(vehicle); step = step.getRawSuperclass().orElseThrow()) {
                builders.add(step);
            }
            // "GroundVehicleEntity" is the kind "Ground vehicle"
            String words = type.getSimpleName().replaceFirst("Entity$", "").replaceAll("(?<=[a-z])(?=[A-Z])", " ");
            String name = words.charAt(0) + words.substring(1).toLowerCase(Locale.ROOT);
            kinds.put(name, build(name, builders));
        }
        return kinds;
    }

    /** The components every vehicle has: the ones the assembly builds. */
    static ComponentGraph shared(JavaClasses classes) {
        return build("Every vehicle", List.of(classes.get(ASSEMBLY)));
    }

    private static ComponentGraph build(String kind, List<JavaClass> builders) {
        Map<JavaClass, List<JavaClass>> parameters = new LinkedHashMap<>();
        for (JavaClass builder : builders) {
            for (JavaConstructorCall call : builder.getConstructorCallsFromSelf()) {
                JavaClass built = call.getTargetOwner();
                if (built.isAssignableTo(COMPONENT)) {
                    parameters.computeIfAbsent(built, key -> new ArrayList<>())
                            .addAll(call.getTarget().getRawParameterTypes());
                }
            }
        }
        SortedMap<String, Node> nodes = new TreeMap<>();
        for (Map.Entry<JavaClass, List<JavaClass>> component : parameters.entrySet()) {
            SortedSet<String> dependsOn = new TreeSet<>();
            for (JavaClass parameter : component.getValue()) {
                for (JavaClass other : parameters.keySet()) {
                    if (!other.equals(component.getKey()) && other.isAssignableTo(parameter.getName())) {
                        dependsOn.add(other.getSimpleName());
                    }
                }
            }
            JavaClass type = component.getKey();
            nodes.put(type.getSimpleName(), new Node(type, dependsOn, phase(type), after(type)));
        }
        return new ComponentGraph(kind, nodes);
    }

    String kind() {
        return this.kind;
    }

    SortedMap<String, Node> nodes() {
        return this.nodes;
    }

    /** How many components of this graph depend on the named one. */
    long fanIn(String name) {
        return this.nodes.values().stream().filter(node -> node.dependsOn().contains(name)).count();
    }

    /** The components of this graph that {@code shared} does not have. */
    List<Node> without(ComponentGraph shared) {
        return this.nodes.values().stream().filter(node -> !shared.nodes.containsKey(node.name())).toList();
    }

    /** A dependency cycle, as the components along it, or empty when there is none. */
    Optional<List<String>> cycle() {
        Set<String> done = new HashSet<>();
        for (String start : this.nodes.keySet()) {
            Optional<List<String>> cycle = cycleFrom(start, new LinkedHashSet<>(), done);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private Optional<List<String>> cycleFrom(String node, LinkedHashSet<String> path, Set<String> done) {
        if (path.contains(node)) {
            List<String> cycle = new ArrayList<>(path);
            cycle = new ArrayList<>(cycle.subList(cycle.indexOf(node), cycle.size()));
            cycle.add(node);
            return Optional.of(cycle);
        }
        if (!done.add(node)) {
            return Optional.empty();
        }
        path.add(node);
        for (String next : this.nodes.get(node).dependsOn()) {
            Optional<List<String>> cycle = cycleFrom(next, path, done);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        path.remove(node);
        return Optional.empty();
    }

    /** Every class implementing the component interface, by name. */
    static List<JavaClass> allComponents(JavaClasses classes) {
        return classes.stream().filter(type -> type.isAssignableTo(COMPONENT) && !type.isInterface())
                .sorted(Comparator.comparing(JavaClass::getName)).toList();
    }

    /** Every type a constructor of the component takes. */
    static Set<JavaClass> constructorParameters(JavaClass type) {
        Set<JavaClass> parameters = new LinkedHashSet<>();
        type.getConstructors().forEach(constructor -> parameters.addAll(constructor.getRawParameterTypes()));
        return parameters;
    }

    /** The phase of the component's {@code @TickOrder}, or an empty string when it declares none. */
    static String phase(JavaClass type) {
        return tickOrder(type).map(order -> order.get("phase").orElseThrow())
                .map(phase -> phase instanceof JavaEnumConstant constant ? constant.name() : String.valueOf(phase))
                .orElse("");
    }

    /** The components named in the component's {@code @TickOrder(after = ...)}. */
    static List<String> after(JavaClass type) {
        return afterClasses(type).stream().map(JavaClass::getSimpleName).sorted().toList();
    }

    static List<JavaClass> afterClasses(JavaClass type) {
        return tickOrder(type).flatMap(order -> order.get("after"))
                .map(value -> List.of((JavaClass[]) value)).orElse(List.of());
    }

    private static Optional<? extends JavaAnnotation<JavaClass>> tickOrder(JavaClass type) {
        return type.tryGetAnnotationOfType(TICK_ORDER);
    }
}
