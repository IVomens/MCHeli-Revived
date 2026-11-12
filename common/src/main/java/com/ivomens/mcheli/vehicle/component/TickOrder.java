package com.ivomens.mcheli.vehicle.component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Where a component's own tick ({@link VehicleComponent#serverTick}, {@link VehicleComponent#clientTick})
 * belongs in the vehicle tick.
 * <p>
 * The tick pipeline checks every declaration when it is built and refuses to run an order that breaks
 * one: the component's step must sit in {@link #phase}, after the steps of every component in
 * {@link #after}. A component may only name a component it depends on (one its constructor takes),
 * which the architecture tests check: an order constraint is a dependency made visible.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TickOrder {

    /** The phase the component's step runs in. */
    TickPhase phase();

    /** Components whose step has to have run earlier in the same tick, because this one reads their result. */
    Class<? extends VehicleComponent>[] after() default {};
}
