package com.ivomens.mcheli.extension;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What a vehicle file asks of other mods: the behaviours it names ({@code Behaviours}) and the movement model it
 * picks ({@code Movement}), each with its decoded settings. Names no installed mod registers are left out when the
 * file is read, with a warning in the content report.
 *
 * @param behaviours in file order
 * @param movement   null for the kind's own movement
 */
public record VehicleAddons(List<ConfiguredBehaviour<?>> behaviours, @Nullable ConfiguredMovement<?> movement) {

    /** A file that names none. */
    public static final VehicleAddons NONE = new VehicleAddons(List.of(), null);

    public VehicleAddons {
        behaviours = List.copyOf(behaviours);
    }
}
