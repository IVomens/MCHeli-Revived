package com.ivomens.mcheli.api.vehicle.extension;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * What a {@link VehicleBehaviour} sees of its vehicle. Read-only: a behaviour changes the world through the
 * level and the entity as any mod does, never through MCHeli's internals.
 *
 * <p><b>Side and thread.</b> The behaviour's own side, on the vehicle level's thread; valid for as long as the
 * behaviour is on its vehicle. Stability: since API 1.1; implemented by MCHeli only, so a minor version may add
 * methods.
 */
public interface BehaviourContext {

    /** The behaviour type's id, as the vehicle file names it. */
    ResourceLocation id();

    /** The vehicle: its entity, kind, crew ({@link Vehicle#seats()}) and state. */
    Vehicle vehicle();

    /** The level the vehicle is in. */
    Level level();

    /** Whether this copy of the vehicle is a client's. */
    boolean isClientSide();

    /** What the pilot holds down, as {@link VehicleControls} describes per side. */
    VehicleControls controls();
}
