package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftInventory;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatInfo;
import com.ivomens.mcheli.sound.ModSounds;
import com.ivomens.mcheli.util.EntityIds;
import com.ivomens.mcheli.util.math.Rotations;
import com.ivomens.mcheli.aircraft.parachute.ParachuteEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;

/** Ejection seats for the pilot and the copilot; each ejection costs a parachute from the inventory. */
public final class EjectionSeats implements VehicleComponent {

    private final AircraftEntity vehicle;
    private final CrewExitComponent crewExit;
    private final PilotHandover handover;
    private final SeatsComponent seats;
    private final BodyComponent body;
    private final PartsComponent parts;
    private final AircraftInventory cargo;
    private boolean fitted;

    public EjectionSeats(AircraftEntity vehicle, CrewExitComponent crewExit, PilotHandover handover,
            SeatsComponent seats, BodyComponent body, PartsComponent parts, AircraftInventory cargo) {
        this.vehicle = vehicle;
        this.crewExit = crewExit;
        this.handover = handover;
        this.seats = seats;
        this.body = body;
        this.parts = parts;
        this.cargo = cargo;
    }

    /** Planes and ships carry ejection seats, if their definition enables them. */
    public void fit() {
        this.fitted = true;
    }

    /** Ejection seats aboard; they only fit single-seaters and two-seaters. */
    public int count() {
        AircraftEntity ac = this.vehicle;
        if (!this.fitted || ac.spec() == null || !ac.spec().file().features().ejectionSeat()) {
            return 0;
        }
        int crew = this.seats.count() + 1;
        return crew <= 2 ? crew : 0;
    }

    public boolean canEjectSeat(@Nullable Entity entity) {
        AircraftEntity ac = this.vehicle;
        int seatId = this.seats.seatIdOf(entity);
        return (seatId != 0 || !ac.isUAV()) && seatId >= 0 && seatId < 2
                && ac.spec() != null && ac.spec().file().features().ejectionSeat();
    }

    /**
     * Ejects the pilot or the copilot, each costing one parachute from the inventory. When the pilot
     * ejects, a non-player copilot goes with them.
     */
    public void ejectSeat(@Nullable Entity entity) {
        AircraftEntity ac = this.vehicle;
        AircraftInventory inventory = this.cargo;
        int seatId = this.seats.seatIdOf(entity);
        if (seatId < 0 || seatId > 1 || !inventory.haveParachute()) {
            return;
        }
        if (seatId == 0) {
            inventory.consumeParachute();
            this.handover.pilotLeaves();
            eject(entity, 0);
            entity = this.seats.riderOf(1);
            if (entity instanceof Player) {
                entity = null;
            }
        }
        if (entity != null && inventory.haveParachute()) {
            inventory.consumeParachute();
            unmountFromSeat(entity);
            eject(entity, 1);
        }
    }

    private void unmountFromSeat(Entity entity) {
        for (SeatEntity seat : this.seats.all()) {
            if (seat != null && seat.getRiddenByEntity() != null && EntityIds.same(seat.getRiddenByEntity(), entity)) {
                entity.stopRiding();
            }
        }
    }

    private void eject(Entity entity, int seatId) {
        AircraftEntity ac = this.vehicle;
        SeatInfo seatInfo = this.seats.info(seatId);
        if (seatInfo != null && seatInfo.pos != null) {
            Vec3 start = this.body.toWorld(seatInfo.pos.x, seatInfo.pos.y + 2.0, seatInfo.pos.z);
            entity.setPos(start.x, start.y, start.z);
        }

        Vec3 kick = Rotations.rotate(0.0, 2.0, 0.0, -ac.getYRot(), -ac.getXRot(), -ac.roll());
        Vec3 motion = ac.getDeltaMovement();
        entity.setDeltaMovement(motion.x + kick.x + (ac.getRandom().nextFloat() - 0.5) * 0.1,
                motion.y + kick.y,
                motion.z + kick.z + (ac.getRandom().nextFloat() - 0.5) * 0.1);
        this.crewExit.spawnParachute(entity, ParachuteEntity.TYPE_EJECTION_SEAT);
        if (Objects.requireNonNull(ac.spec()).parts().haveCanopy() && this.parts.isCanopyClosed()) {
            this.parts.openCanopyForEjection();
        }
        entity.playSound(ModSounds.getSound("eject_seat"), 5.0F, 1.0F);
    }
}
