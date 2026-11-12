package com.ivomens.mcheli.util.state;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static field that only caches what can be computed again: dropping its value changes how
 * fast the game runs, never what it does. A cache always has a reset hook, a static method without
 * parameters in the same class that drops the cached value, named by {@link #reset()}; whoever owns
 * the cache's lifecycle (a resource reload, leaving a world) calls it.
 *
 * <p>It is one of the two kinds of static mutable state the architecture rules allow (see
 * docs/ARCHITECTURE.md Part 2 §3.2), and the rule checks that the reset hook exists.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface StaticCache {

    /** The static method without parameters, in the field's class, that empties the cache. */
    String reset();
}
