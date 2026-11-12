package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.content.def.vehicle.CrewEntries.CameraSpec;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One entry of YAML {@code Racks}: a place that carries another vehicle, such as a cargo hook.
 *
 * @param entry       where a vehicle has to be to be loaded, kept as the rack's camera position
 * @param names       the vehicles that fit, by file name
 * @param range       how close a vehicle must come to the entry point
 * @param excludeWith seats and racks that cannot be taken while this one is
 */
public record Rack(Vec3 position, @Nullable Vec3 unmountPosition, CameraSpec entry, List<String> names,
                   float range, float yaw, float pitch, boolean rotating, SeatExclusions excludeWith) {

    public Rack {
        names = List.copyOf(names);
    }

    Rack withExclusions(SeatExclusions exclusions) {
        return new Rack(this.position, this.unmountPosition, this.entry, this.names, this.range, this.yaw,
                this.pitch, this.rotating, exclusions);
    }
}
