package com.ivomens.mcheli.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Turns imported classes into violation entries: stable, readable ids, each with one piece of
 * evidence for the failure message.
 */
final class Violations {

    private Violations() {
    }

    /**
     * Top-level classes that contain a class matching {@code origin} which depends on a class matching
     * {@code target}. Nested, anonymous and lambda classes count towards their top-level class, so the
     * entries survive the compiler renumbering {@code Foo$1}.
     */
    static SortedMap<String, String> dependencies(JavaClasses classes, DescribedPredicate<JavaClass> origin,
                                                  DescribedPredicate<JavaClass> target) {
        SortedMap<String, String> found = new TreeMap<>();
        for (JavaClass javaClass : classes) {
            if (!origin.test(javaClass)) {
                continue;
            }
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                if (target.test(dependency.getTargetClass())) {
                    found.putIfAbsent(topLevelName(javaClass), dependency.getDescription());
                }
            }
        }
        return found;
    }

    /** Fields matching the predicate, as {@code Owner#field}. Compiler-generated fields are skipped. */
    static SortedMap<String, String> fields(JavaClasses classes, Predicate<JavaField> predicate) {
        SortedMap<String, String> found = new TreeMap<>();
        for (JavaClass javaClass : classes) {
            for (JavaField field : javaClass.getFields()) {
                if (!field.getModifiers().contains(JavaModifier.SYNTHETIC) && predicate.test(field)) {
                    found.put(javaClass.getName() + "#" + field.getName(), field.getFullName()
                            + " is " + field.getModifiers() + " " + field.getRawType().getSimpleName());
                }
            }
        }
        return found;
    }

    static String topLevelName(JavaClass javaClass) {
        JavaClass outermost = javaClass;
        while (outermost.getEnclosingClass().isPresent()) {
            outermost = outermost.getEnclosingClass().get();
        }
        return outermost.getName();
    }
}
