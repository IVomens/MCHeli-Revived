package com.ivomens.mcheli.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

import java.util.Optional;
import java.util.Set;

/**
 * The feature slices the package cycle rule compares: one per top-level package below
 * {@code com.ivomens.mcheli}, with two groupings. The packages of the vehicle core
 * ({@link #VEHICLE_CORE_PACKAGES}) are one slice, {@code vehicles}: the vehicle core is one subsystem with
 * its own rewrite, and how its legacy packages depend on each other is its own business. The vehicle
 * core's read-only API, {@code api}, is a slice of its own, so the features that use it never depend on
 * the vehicle core. The {@code :core} module ({@code com.ivomens.mcheli.core}) is one slice, {@code core};
 * its module boundary already keeps it from depending on any other slice, and its own packages are
 * checked for cycles by a rule of their own. The mod's entry point in the root package belongs to no
 * slice.
 */
final class FeatureSlices implements SliceAssignment {

    static final FeatureSlices INSTANCE = new FeatureSlices();

    private static final String MOD = "com.ivomens.mcheli.";
    private static final Set<String> VEHICLE_CORE_PACKAGES = Set.of("aircraft", "helicopter", "plane", "ship",
            "tank", "vehicle");
    private static final String VEHICLE_CORE = "vehicles";

    private FeatureSlices() {
    }

    @Override
    public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
        return sliceOf(javaClass).map(SliceIdentifier::of).orElseGet(SliceIdentifier::ignore);
    }

    @Override
    public String getDescription() {
        return "top-level feature packages of " + MOD + "*, the vehicle core as one";
    }

    static Optional<String> sliceOf(JavaClass javaClass) {
        String name = javaClass.getPackageName();
        if (!name.startsWith(MOD)) {
            return Optional.empty();
        }
        String rest = name.substring(MOD.length());
        String top = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
        return Optional.of(VEHICLE_CORE_PACKAGES.contains(top) ? VEHICLE_CORE : top);
    }
}
