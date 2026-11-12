package com.ivomens.mcheli.util.state;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static field that is assigned while the game registers its content and then only read: a
 * registry, a registration handle the loader hands out, or a value the loader passes in during mod
 * construction. Such a field is written in one place, on one thread, before anything reads it.
 *
 * <p>It is one of the two kinds of static mutable state the architecture rules allow (see
 * docs/ARCHITECTURE.md Part 2 §3.2); everything else is instance state of a per-client or per-server
 * service. Do not use it for state that changes while the game runs.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RegistryHolder {
}
