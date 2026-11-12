package com.ivomens.mcheli.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;

/**
 * Writes docs/COMPONENTS.md, the vehicle components' dependency graph per vehicle kind, from the
 * compiled classes: {@code ./gradlew :architecture:writeComponentGraph}. {@code ComponentRulesTest}
 * fails while the committed file differs from what the classes give.
 */
public final class ComponentGraphDoc {

    private static final String HEADER = """
            # Vehicle components

            <!-- Generated from the compiled classes by `./gradlew :architecture:writeComponentGraph`; do not edit.
                 ComponentRulesTest fails while this file is stale. -->

            Every vehicle is made of components (`VehicleComponent`). A component gets the components it works
            with through its constructor, so its constructor parameters are its dependencies: an arrow points
            from a component to one it depends on. `AircraftComponents` builds the components every vehicle
            shares in that order, and each vehicle kind's entity builds its own on top of them. The graph has no
            cycles; the architecture tests check that, and that nothing reaches a component through the vehicle
            entity instead (see [ARCHITECTURE_RULES.md](ARCHITECTURE_RULES.md)).

            A component with a tick of its own declares the phase it ticks in and the components it must tick
            after (`@TickOrder`). Every component named there is one it depends on; the tick pipeline checks the
            order when it is built. [ARCHITECTURE.md](ARCHITECTURE.md) Part 1 §2.4 and §2.6 describe the model.

            *Fan-in* counts the components of the vehicle that depend on a component. *Fan-out* counts the
            collaborators its constructor takes: other components and narrow ports, not the host entity,
            plain values or settings. A constructor takes at most 7 (see
            [ARCHITECTURE_RULES.md](ARCHITECTURE_RULES.md), "Components").

            """;

    private ComponentGraphDoc() {
    }

    /** The whole document for the classes. */
    static String generate(JavaClasses classes) {
        ComponentGraph shared = ComponentGraph.shared(classes);
        SortedMap<String, ComponentGraph> kinds = ComponentGraph.byKind(classes);
        StringBuilder doc = new StringBuilder(HEADER);
        doc.append("## Every vehicle\n\n");
        doc.append("The components `AircraftComponents` builds, which every vehicle kind has.\n\n");
        section(doc, shared, shared.nodes().values());
        for (ComponentGraph kind : kinds.values()) {
            doc.append("## ").append(kind.kind()).append("\n\n");
            List<ComponentGraph.Node> own = kind.without(shared);
            if (own.isEmpty()) {
                doc.append("Only the components every vehicle has.\n\n");
                continue;
            }
            doc.append("What a ").append(kind.kind().toLowerCase(Locale.ROOT))
                    .append(" adds to the components every vehicle has.\n\n");
            section(doc, kind, own);
        }
        return doc.toString().stripTrailing() + "\n";
    }

    private static void section(StringBuilder doc, ComponentGraph graph, Collection<ComponentGraph.Node> nodes) {
        doc.append("| Component | Ticks in | Ticks after | Fan-in | Fan-out | Depends on |\n"
                + "|---|---|---|---|---|---|\n");
        for (ComponentGraph.Node node : nodes) {
            doc.append("| `").append(node.name()).append("` | ").append(orDash(node.phase())).append(" | ")
                    .append(orDash(String.join(", ", node.after()))).append(" | ")
                    .append(graph.fanIn(node.name())).append(" | ")
                    .append(Collaborators.of(node.type()).size()).append(" | ")
                    .append(orDash(String.join(", ", node.dependsOn()))).append(" |\n");
        }
        doc.append("\n```mermaid\nflowchart LR\n");
        for (ComponentGraph.Node node : nodes) {
            if (node.dependsOn().isEmpty()) {
                doc.append("    ").append(node.name()).append('\n');
            }
            for (String dependency : node.dependsOn()) {
                doc.append("    ").append(node.name()).append(" --> ").append(dependency).append('\n');
            }
        }
        doc.append("```\n\n");
    }

    private static String orDash(String text) {
        return text.isEmpty() ? "-" : text;
    }

    /** The committed document. */
    static Path path() {
        return Path.of(System.getProperty("architecture.componentsDoc", "../docs/COMPONENTS.md"));
    }

    static JavaClasses importMainClasses() {
        return new ClassFileImporter().importPaths(ArchitecturePaths.mainClasses());
    }

    public static void main(String[] args) throws IOException {
        Path out = path();
        Files.writeString(out, generate(importMainClasses()), StandardCharsets.UTF_8);
        System.out.println("Wrote " + out.toAbsolutePath());
    }
}
