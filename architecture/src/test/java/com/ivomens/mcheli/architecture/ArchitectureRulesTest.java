package com.ivomens.mcheli.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.LocationProvider;
import com.tngtech.archunit.lang.ArchRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The structural rules from docs/ARCHITECTURE.md §7, checked against the compiled main classes.
 *
 * <p>Every rule is absolute: a violation fails the build and is fixed in the code. See
 * docs/ARCHITECTURE_RULES.md for what each rule protects.
 */
@AnalyzeClasses(locations = ArchitectureRulesTest.MainClasses.class)
class ArchitectureRulesTest {

    private static final String MOD = "com.ivomens.mcheli";

    /** Loader glue. Everything else is common code that must run on any loader. */
    private static final String NEOFORGE_GLUE = MOD + ".neoforge..";
    private static final String FABRIC_GLUE = MOD + ".fabric..";
    private static final String[] LOADER_PACKAGES = {NEOFORGE_GLUE, FABRIC_GLUE};

    /** Where client-only code is allowed to live: any package named {@code client}. */
    private static final String[] CLIENT_PACKAGES = {MOD + "..client.."};

    /** Vehicle and weapon logic that has to behave the same on every machine. */
    private static final String[] GAMEPLAY_PACKAGES = {
            MOD + ".aircraft..", MOD + ".helicopter..", MOD + ".plane..", MOD + ".tank..",
            MOD + ".vehicle..", MOD + ".ship..", MOD + ".weapon..", MOD + ".flare..", MOD + ".combat..",
            MOD + ".equipment.."
    };

    /** Rendering code, ours and the game's. Content parsing has to work on a dedicated server. */
    private static final String[] CLIENT_RENDERING_PACKAGES = {
            "net.minecraft.client..", "com.mojang.blaze3d..", MOD + "..client.."
    };

    /** The {@code :core} module: everything of the mod that needs no Minecraft. */
    private static final String CORE = MOD + ".core..";
    private static final String CORE_SIM = MOD + ".core.sim..";

    /** What {@code :core} may use: Java, its pure libraries (see core/build.gradle) and itself. */
    private static final String[] CORE_DEPENDENCIES = {"java..", "javax.annotation..", "org.joml..", CORE};

    /** The public API, the {@code :api} module: what other mods compile against. */
    private static final String API = MOD + ".api..";

    /**
     * What the API may use: Java, the nullness annotations, the Minecraft types addons share with it
     * (entities, levels, vectors, hit results) and itself. Never the rest of the mod, a loader or a library.
     */
    private static final String[] API_DEPENDENCIES = {"java..", "javax.annotation..", "net.minecraft..", API};

    /** The game, Mojang's libraries and the mod loaders. */
    private static final String[] GAME_AND_LOADER_PACKAGES = {
            "net.minecraft..", "com.mojang..", "net.neoforged..", "net.fabricmc..", "cpw.mods..", "org.spongepowered.."
    };

    /** Sources of randomness and wall-clock time that make a simulation unrepeatable. */
    private static final Set<String> NONDETERMINISTIC_TYPES = Set.of(
            "java.util.Random", "java.util.concurrent.ThreadLocalRandom");
    private static final Set<String> NONDETERMINISTIC_METHODS = Set.of(
            "java.lang.Math.random", "java.lang.StrictMath.random",
            "java.lang.System.nanoTime", "java.lang.System.currentTimeMillis");

    @ArchTest
    static void eachLoaderApiOnlyInItsGlue(JavaClasses classes) {
        SortedMap<String, String> found = new TreeMap<>();
        found.putAll(Violations.dependencies(classes, not(resideInAnyPackage(NEOFORGE_GLUE)),
                resideInAnyPackage("net.neoforged..")));
        found.putAll(Violations.dependencies(classes, not(resideInAnyPackage(FABRIC_GLUE)),
                resideInAnyPackage("net.fabricmc..")));
        RuleCheck.noViolations("Only the neoforge package uses NeoForge APIs, and only the fabric package Fabric APIs",
                found);
    }

    @ArchTest
    static void onlyClientPackagesUseClientClasses(JavaClasses classes) {
        RuleCheck.noViolations("Only client packages use net.minecraft.client or our own client packages",
                Violations.dependencies(classes,
                        not(resideInAnyPackage(CLIENT_PACKAGES)),
                        resideInAnyPackage("net.minecraft.client..", MOD + "..client..")));
    }

    @ArchTest
    static void noPublicMutableFields(JavaClasses classes) {
        RuleCheck.noViolations("No public non-final fields",
                Violations.fields(classes, field -> field.getModifiers().contains(JavaModifier.PUBLIC)
                        && !field.getModifiers().contains(JavaModifier.FINAL)));
    }

    /**
     * Static mutable state is allowed only where it is declared as one of the two kinds §3.2 permits: a
     * {@code @RegistryHolder}, or a {@code @StaticCache} whose reset hook (a static method without
     * parameters in the same class) exists. A cache naming a missing hook is a violation too.
     */
    @ArchTest
    static void noStaticMutableFields(JavaClasses classes) {
        RuleCheck.noViolations("No non-final static fields, except registry holders and resettable caches",
                Violations.fields(classes, field -> field.getModifiers().contains(JavaModifier.STATIC)
                        && !field.getModifiers().contains(JavaModifier.FINAL)
                        && !StaticState.isDeclared(field)));
    }

    @ArchTest
    static void gameplayAvoidsRandomAndWallClock(JavaClasses classes) {
        // One entry per class and API, so removing nanoTime from a class shows up even if it still uses Random.
        SortedMap<String, String> found = new TreeMap<>();
        // client code draws frames and may time them; only the simulation has to replay
        for (JavaClass javaClass : classes.that(resideInAnyPackage(GAMEPLAY_PACKAGES)
                .and(not(resideInAnyPackage(CLIENT_PACKAGES))))) {
            String owner = Violations.topLevelName(javaClass);
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getName();
                if (NONDETERMINISTIC_TYPES.contains(target)) {
                    found.putIfAbsent(owner + " " + target, dependency.getDescription());
                }
            }
            for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                String target = call.getTargetOwner().getName() + "." + call.getName();
                if (NONDETERMINISTIC_METHODS.contains(target)) {
                    found.putIfAbsent(owner + " " + target + "()", call.getDescription());
                }
            }
        }
        RuleCheck.noViolations(
                "Gameplay packages use no java.util.Random, Math.random, System.nanoTime or currentTimeMillis", found);
    }

    @ArchTest
    static void platformDoesNotDependOnGameplay(JavaClasses classes) {
        RuleCheck.noViolations("The platform package does not depend on gameplay packages",
                Violations.dependencies(classes,
                        resideInAnyPackage(MOD + ".platform.."),
                        resideInAnyPackage(GAMEPLAY_PACKAGES)));
    }

    @ArchTest
    static void contentParsingDoesNotDependOnRendering(JavaClasses classes) {
        RuleCheck.noViolations(
                "Content (definitions, packs, parsing and registries) does not depend on client rendering",
                Violations.dependencies(classes,
                        resideInAnyPackage(MOD + ".content.."),
                        resideInAnyPackage(CLIENT_RENDERING_PACKAGES)));
    }

    @ArchTest
    static void commonCodeDoesNotDependOnLoaderGlue(JavaClasses classes) {
        RuleCheck.noViolations("Common code does not depend on the neoforge or fabric packages",
                Violations.dependencies(classes,
                        not(resideInAnyPackage(LOADER_PACKAGES)),
                        resideInAnyPackage(LOADER_PACKAGES)));
    }

    /**
     * Feature packages do not depend on each other in a cycle, so each can be understood, tested and
     * replaced on its own.
     */
    @ArchTest
    static final ArchRule featurePackagesFormNoCycles = slices().assignedFrom(FeatureSlices.INSTANCE)
            .should().beFreeOfCycles();

    /**
     * The {@code :core} module needs no Minecraft: it uses Java, the few pure libraries its build lists and
     * itself, never the rest of the mod. The module boundary enforces this at compile time; the rule
     * guards the shipped classes.
     */
    @ArchTest
    static final ArchRule coreUsesOnlyJavaAndPureLibraries = classes()
            .that().resideInAPackage(CORE)
            .should().onlyDependOnClassesThat().resideInAnyPackage(CORE_DEPENDENCIES);

    /** The {@code :core} module never reaches Minecraft, Mojang's libraries or a mod loader, directly or not. */
    @ArchTest
    static final ArchRule coreUsesNoMinecraftOrLoader = noClasses()
            .that().resideInAPackage(CORE)
            .should().dependOnClassesThat().resideInAnyPackage(GAME_AND_LOADER_PACKAGES);

    /** The areas of {@code :core} (sim, hud, wingman, content, ballistics, guidance, ...) form no cycles. */
    @ArchTest
    static final ArchRule corePackagesFormNoCycles = slices()
            .matching(MOD + ".core.(*)..")
            .should().beFreeOfCycles();

    /**
     * The physics stays self-contained inside {@code :core}: Java, JOML and itself, so the vehicle
     * models can be read and tested on their own.
     */
    @ArchTest
    static final ArchRule simUsesOnlyJavaAndJoml = classes()
            .that().resideInAPackage(CORE_SIM)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "org.joml..", CORE_SIM);

    /**
     * The public API depends on nothing of the mod outside itself, on no loader and no library, so an addon
     * compiled against it needs nothing else, and the mod's internals can change without breaking addons.
     * No allow-list: the module was written clean. Its module boundary enforces the same at compile time.
     */
    @ArchTest
    static final ArchRule apiUsesOnlyJavaMinecraftAndItself = classes()
            .that().resideInAPackage(API)
            .should().onlyDependOnClassesThat().resideInAnyPackage(API_DEPENDENCIES);

    /** The API works on a dedicated server: it names no client class. */
    @ArchTest
    static final ArchRule apiUsesNoClientClasses = noClasses()
            .that().resideInAPackage(API)
            .should().dependOnClassesThat().resideInAnyPackage("net.minecraft.client..", "com.mojang.blaze3d..");

    /**
     * Every class in the API's packages comes from the {@code :api} module. Common code declaring a class
     * there would ship it as if it were API without the signature check of api.txt seeing it.
     */
    @ArchTest
    static void apiPackagesHoldOnlyTheApiModule(JavaClasses classes) {
        Path apiSources = ArchitecturePaths.mainSources().stream()
                .filter(root -> root.normalize().endsWith(Path.of("api", "src", "main", "java")))
                .findFirst().orElseThrow(() -> new AssertionError("the api module's sources are not configured"));
        Set<String> strays = new TreeSet<>();
        for (JavaClass javaClass : classes.that(resideInAnyPackage(API))) {
            String topLevel = Violations.topLevelName(javaClass);
            if (!Files.isRegularFile(apiSources.resolve(topLevel.replace('.', '/') + ".java"))) {
                strays.add(topLevel);
            }
        }
        if (!strays.isEmpty()) {
            throw new AssertionError("Classes in com.ivomens.mcheli.api outside the api module: " + strays);
        }
    }

    /** Imports exactly the main source set's output, wherever Gradle put it. */
    static final class MainClasses implements LocationProvider {

        @Override
        public Set<Location> get(Class<?> testClass) {
            return ArchitecturePaths.mainClasses().stream().map(Location::of).collect(Collectors.toSet());
        }
    }
}
