package com.ivomens.mcheli.api.vehicle.extension;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the installed mods registered through {@link VehicleExtensionProvider}, and which of it a vehicle uses.
 *
 * <p><b>Side and thread.</b> Both sides. The registered ids are fixed once MCHeli has loaded its content and may
 * be read from any thread; the per-vehicle lookups read the vehicle's type, so call them on the vehicle level's
 * thread. Stability: since API 1.1; implemented by MCHeli only, so a minor version may add methods.
 */
public interface VehicleExtensions {

    /** The ids of every registered behaviour type; empty when no mod registers one. */
    Set<ResourceLocation> behaviourTypes();

    /** The ids of every registered movement model; empty when no mod registers one. */
    Set<ResourceLocation> movementModels();

    /**
     * The behaviour types the vehicle's file names and that loaded, in file order. Empty before the vehicle knows
     * its type, and for a file that names none; ids that are not registered, or whose settings were rejected,
     * are left out (the content report lists them).
     */
    List<ResourceLocation> behavioursOf(Vehicle vehicle);

    /**
     * The movement model the vehicle's file picks, when it is registered and its settings loaded; empty when the
     * vehicle moves the way its kind does.
     */
    Optional<ResourceLocation> movementModelOf(Vehicle vehicle);
}
