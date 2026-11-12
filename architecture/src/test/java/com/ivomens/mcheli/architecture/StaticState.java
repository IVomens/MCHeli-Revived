package com.ivomens.mcheli.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;

import java.util.Optional;

/**
 * The static mutable fields docs/ARCHITECTURE.md Part 2 §3.2 allows: those annotated
 * {@code @RegistryHolder}, and those annotated {@code @StaticCache} whose declaring class has the
 * named reset hook. The annotations are matched by name, so the rule needs no production classes on
 * its classpath.
 */
final class StaticState {

    private static final String PACKAGE = "com.ivomens.mcheli.util.state.";
    static final String REGISTRY_HOLDER = PACKAGE + "RegistryHolder";
    static final String STATIC_CACHE = PACKAGE + "StaticCache";

    private StaticState() {
    }

    /** Whether the field is declared as allowed static state, with a reset hook if it is a cache. */
    static boolean isDeclared(JavaField field) {
        if (field.isAnnotatedWith(REGISTRY_HOLDER)) {
            return true;
        }
        Optional<JavaAnnotation<JavaField>> cache = field.tryGetAnnotationOfType(STATIC_CACHE);
        return cache.isPresent() && hasResetHook(field, String.valueOf(cache.get().get("reset").orElse("")));
    }

    private static boolean hasResetHook(JavaField field, String name) {
        for (JavaMethod method : field.getOwner().getMethods()) {
            if (method.getName().equals(name) && method.getRawParameterTypes().isEmpty()
                    && method.getModifiers().contains(JavaModifier.STATIC)) {
                return true;
            }
        }
        return false;
    }
}
