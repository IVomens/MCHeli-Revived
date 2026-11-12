package com.ivomens.mcheli.wingman.control;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.PlaneEntity;
import com.ivomens.mcheli.core.sim.model.RotorcraftHandling;
import com.ivomens.mcheli.core.wingman.brain.Airframe;
import com.ivomens.mcheli.core.wingman.brain.Controls;
import com.ivomens.mcheli.core.wingman.brain.LeaderState;
import com.ivomens.mcheli.core.wingman.brain.Point;
import com.ivomens.mcheli.core.wingman.brain.RecordedControls;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Reads the parts of a {@code Perception} that come straight from entities. */
final class Observer {

    private Observer() {
    }

    static Airframe airframe(AircraftEntity aircraft) {
        Airframe.Kind kind;
        if (aircraft instanceof HelicopterEntity) {
            kind = Airframe.Kind.HELICOPTER;
        } else if (aircraft instanceof PlaneEntity plane) {
            kind = plane.nozzles().isFitted() ? Airframe.Kind.VTOL_PLANE : Airframe.Kind.PLANE;
        } else {
            kind = Airframe.Kind.OTHER;
        }
        float gravity = Airframe.DEFAULT_GRAVITY;
        float speedLimit = 0.0F;
        if (aircraft instanceof HelicopterEntity heli && heli.definition() != null) {
            RotorcraftHandling handling = heli.physics().handling(RotorcraftHandling.class);
            gravity = handling.gravity().air();
            speedLimit = handling.speedLimit();
        }
        return new Airframe(kind, AircraftControls.pilotOf(aircraft) instanceof Player, aircraft.fuel().amount(),
                aircraft.fuel().capacity(), gravity, speedLimit);
    }

    /** Controls that start from the aircraft as it is now and keep their changes for later. */
    static RecordedControls recordedControls(AircraftEntity aircraft) {
        Vec3 m = aircraft.getDeltaMovement();
        return new RecordedControls(new Point(aircraft.getX(), aircraft.getY(), aircraft.getZ()),
                new Point(m.x, m.y, m.z), aircraft.getYRot(), aircraft.getXRot(), aircraft.roll(),
                aircraft.engine().throttle(), aircraft.onGround(), aircraft.engine().isCrippled());
    }

    /**
     * The leader as it is now. A leader that is itself a wingman already flown this tick is seen with
     * the controls the brain gave it, as if they had been applied.
     */
    static LeaderState leader(Entity leader, @Nullable Controls decided) {
        Vec3 m = leader.getDeltaMovement();
        Point position = new Point(leader.getX(), leader.getY(), leader.getZ());
        Point motion = new Point(m.x, m.y, m.z);
        boolean playerAtControls = AircraftControls.pilotOf(leader) instanceof Player;
        if (!(leader instanceof AircraftEntity aircraft)) {
            return new LeaderState(position, leader.getYRot(), 0f, Double.NaN, motion, false, false, playerAtControls);
        }
        boolean gearFolded = aircraft.parts().isLandingGearFolded();
        if (decided != null) {
            return new LeaderState(decided.position(), decided.yaw(), decided.roll(), decided.throttle(),
                    decided.motion(), true, gearFolded, playerAtControls);
        }
        return new LeaderState(position, aircraft.getYRot(), aircraft.roll(), aircraft.engine().throttle(), motion,
                true, gearFolded, playerAtControls);
    }
}
