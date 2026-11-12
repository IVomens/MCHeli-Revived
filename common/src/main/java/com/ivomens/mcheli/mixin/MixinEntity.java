package com.ivomens.mcheli.mixin;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.api.vehicle.VehicleSeat;
import com.ivomens.mcheli.event.ApiEvents;
import com.ivomens.mcheli.util.Terrain;
import com.ivomens.mcheli.vehicle.world.AircraftIndex;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Aircraft (ships especially) have extra collision boxes beyond their entity bounding box, e.g. a
 * carrier deck. They are appended to the entity collision list used by {@code Entity#collide}.
 * <p>
 * Every entity move runs that check, so aircraft are also kept in their level's
 * {@link AircraftIndex}, which rules out the usual case (no aircraft anywhere near) without an
 * entity search. An aircraft's own move skips the block search where only air is around it.
 * <p>
 * On the server it also tells the API's listeners when a vehicle leaves its level and when an entity
 * takes or leaves one of a vehicle's seats ({@link ApiEvents}).
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    /** How far an aircraft's extra boxes may reach beyond its own bounding box. */
    private static final double MCHELI_SEARCH_RADIUS = 5.0;
    /** Extra margin so boxes just outside the movement box are still found. */
    private static final double MCHELI_QUERY_MARGIN = 0.25;

    @Shadow
    private EntityInLevelCallback levelCallback;

    /** The callback is set when the entity enters the level's entity storage and reset when it leaves. */
    @Inject(method = "setLevelCallback", at = @At("TAIL"))
    private void mcheli$indexAircraftMembership(EntityInLevelCallback callback, CallbackInfo ci) {
        if ((Object) this instanceof AircraftEntity aircraft) {
            AircraftIndex index = AircraftIndex.of(aircraft.level());
            if (callback == EntityInLevelCallback.NULL) {
                index.remove(aircraft);
                if (!aircraft.level().isClientSide && aircraft.getRemovalReason() != null) {
                    ApiEvents.vehicleRemoved(aircraft, aircraft.getRemovalReason());
                }
            } else {
                index.add(aircraft);
            }
        }
    }

    /** The API's {@code SeatBoarded}: the passenger is aboard the vehicle itself (the pilot) or one of its seats. */
    @Inject(method = "addPassenger", at = @At("TAIL"))
    private void mcheli$announceBoarding(Entity passenger, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Vehicle vehicle = mcheli$vehicleOfSeat(self);
        if (vehicle != null) {
            int seatId = vehicle.seats().seatIdOf(passenger);
            if (seatId >= 0) {
                ApiEvents.seatBoarded(vehicle, passenger, seatId);
            }
        }
    }

    /** The API's {@code SeatLeft}, while the passenger is still listed in its seat. */
    @Inject(method = "removePassenger", at = @At("HEAD"))
    private void mcheli$announceLeaving(Entity passenger, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Vehicle vehicle = mcheli$vehicleOfSeat(self);
        if (vehicle != null) {
            int seatId = vehicle.seats().seatIdOf(passenger);
            if (seatId >= 0) {
                ApiEvents.seatLeft(vehicle, passenger, seatId);
            }
        }
    }

    /** The vehicle a server-side entity is a seat of: the vehicle itself or one of its seat entities. */
    @Unique
    @Nullable
    private static Vehicle mcheli$vehicleOfSeat(Entity self) {
        if (self.level().isClientSide) {
            return null;
        }
        return switch (self) {
            case Vehicle vehicle -> vehicle;
            case VehicleSeat seat -> seat.getParent();
            default -> null;
        };
    }

    @Inject(method = "setPosRaw", at = @At("TAIL"))
    private void mcheli$indexAircraftMovement(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof AircraftEntity aircraft && this.levelCallback != EntityInLevelCallback.NULL) {
            AircraftIndex.of(aircraft.level()).move(aircraft);
        }
    }

    /**
     * A vehicle's block collision finds nothing where every block around its move is air, which is where
     * flying vehicles spend their time; the search over those blocks is skipped there. Every other case,
     * and every other entity, gets vanilla's search.
     */
    @WrapOperation(method = "collectColliders",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"))
    private static Iterable<VoxelShape> mcheli$skipOpenAir(Level level, @Nullable Entity entity, AABB box,
            Operation<Iterable<VoxelShape>> op) {
        if (entity instanceof AircraftEntity && Terrain.isOnlyAirAround(level, box)) {
            return List.of();
        }
        return op.call(level, entity, box);
    }

    @WrapOperation(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> mcheli$addAircraftBoxes(Level level, Entity entity, AABB box, Operation<List<VoxelShape>> op) {
        List<VoxelShape> original = op.call(level, entity, box);
        Entity self = (Entity) (Object) this;
        if (self instanceof AircraftEntity || self.noPhysics) {
            return original;
        }
        if (!AircraftIndex.of(level).mayFindAircraft(box, MCHELI_QUERY_MARGIN + MCHELI_SEARCH_RADIUS)) {
            return original;
        }
        AABB query = box.inflate(MCHELI_QUERY_MARGIN);
        List<AircraftEntity> aircraft = level.getEntitiesOfClass(AircraftEntity.class,
                query.inflate(MCHELI_SEARCH_RADIUS));
        if (aircraft.isEmpty()) {
            return original;
        }
        List<VoxelShape> shapes = null;
        for (AircraftEntity ac : aircraft) {
            if (ac.isRemoved() || ac.seats().isAboard(self)) {
                continue;
            }
            AABB[] boxes = ac.partBoxes().collisionBoxes();
            if (boxes == null) {
                continue;
            }
            for (AABB extra : boxes) {
                if (extra != null && extra.intersects(query)) {
                    if (shapes == null) {
                        shapes = new ArrayList<>(original);
                    }
                    shapes.add(Shapes.create(extra));
                }
            }
        }
        return shapes != null ? shapes : original;
    }
}
