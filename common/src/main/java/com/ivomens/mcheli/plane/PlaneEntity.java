package com.ivomens.mcheli.plane;

import com.ivomens.mcheli.aircraft.AircraftEntityTypes;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Planes: the fixed-wing flight model, with afterburner flames that lean into turns. */
public class PlaneEntity extends AbstractPlaneEntity {

    private final ExhaustFlames exhaustFlames = new ExhaustFlames(this);

    public PlaneEntity(EntityType<? extends PlaneEntity> type, Level level) {
        super(type, level);
    }

    public PlaneEntity(Level level) {
        this(AircraftEntityTypes.PLANE.get(), level);
    }

    @Override
    public VehicleKind kind() {
        return VehicleKind.PLANE;
    }

    @Nullable
    @Override
    protected VehicleSpec findSpec(String typeName) {
        return VehicleTypes.get(VehicleKind.PLANE, typeName).orElse(null);
    }

    public ExhaustFlames exhaustFlames() {
        return this.exhaustFlames;
    }

    @Override
    protected void animateWithKeys(float frameSeconds) {
        this.exhaustFlames.followKeys(frameSeconds);
    }
}
