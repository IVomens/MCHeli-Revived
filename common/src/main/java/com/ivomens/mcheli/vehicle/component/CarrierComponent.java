package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.core.sim.math.LegacyMath;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehiclePeers;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.aircraft.SeatRackInfo;
import com.ivomens.mcheli.vehicle.settings.CarriageRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The vehicle riding something else: a minecart it hops onto (and off again at full throttle), or
 * a rack on another vehicle that carries it.
 */
@TickOrder(phase = TickPhase.LIFECYCLE)
public final class CarrierComponent implements VehicleComponent {

    /** Ticks after leaving a cart before the vehicle looks for one again. */
    private static final int REMOUNT_DELAY = 20;
    private static final int MIN_AGE_TO_MOUNT = 20;
    private static final int MOUNT_CHECK_INTERVAL = 5;
    private static final double JUMP_OFF_THROTTLE = 0.8;

    private final AircraftEntity vehicle;
    private final EngineComponent engine;
    private final BodyComponent body;
    private final RollComponent roll;
    private final Supplier<CarriageRules> carriage;
    private int remountDelay;
    /** The vehicle was taken off a minecart on purpose, so it should not hop straight back on. */
    private boolean leftCartOnPurpose;
    @Nullable
    private Entity lastCarrier;
    private Consumer<Entity> onLoaded = carrier -> {
    };

    public CarrierComponent(AircraftEntity vehicle, EngineComponent engine, BodyComponent body, RollComponent roll,
            Supplier<CarriageRules> carriage) {
        this.vehicle = vehicle;
        this.engine = engine;
        this.body = body;
        this.roll = roll;
        this.carriage = carriage;
    }

    /** What the vehicle type does when it is loaded onto a carrier. */
    public void onLoaded(Consumer<Entity> hook) {
        this.onLoaded = hook;
    }

    /** Hops onto a nearby empty cart, and while on one turns with it and levels out; full throttle jumps off. */
    @Override
    public void serverTick(TickContext ctx) {
        AircraftEntity ac = this.vehicle;
        if (this.remountDelay == 0 && ctx.age() > MIN_AGE_TO_MOUNT && ctx.age() % MOUNT_CHECK_INTERVAL == 0
                && mayRideMinecarts()) {
            mountNearbyMinecart();
        }
        countDownRemountDelay();
        Entity carrier = ac.getVehicle();
        if (carrier != null) {
            rideAlong(carrier);
        }
    }

    @Override
    public void clientTick(TickContext ctx) {
        countDownRemountDelay();
    }

    private void countDownRemountDelay() {
        if (this.remountDelay > 0) {
            this.remountDelay--;
        }
    }

    /** The config decides per vehicle kind; emplaced vehicles pinned in place never ride. */
    private boolean mayRideMinecarts() {
        return this.carriage.get().ridesMinecarts(this.vehicle.kind());
    }

    /** Hops onto a nearby empty minecart, unless the vehicle was just taken off one. */
    private void mountNearbyMinecart() {
        AircraftEntity ac = this.vehicle;
        if (ac.getVehicle() != null) {
            return;
        }
        int range = this.leftCartOnPurpose ? 6 : 2;
        List<Minecart> carts = ac.level().getEntitiesOfClass(Minecart.class,
                this.body.hullBox().inflate(range, range, range));
        for (Minecart cart : carts) {
            if (this.leftCartOnPurpose) {
                return;
            }
            if (!cart.isVehicle() && cart.isPushable()) {
                this.remountDelay = REMOUNT_DELAY;
                ac.startRiding(cart);
                return;
            }
        }
        this.leftCartOnPurpose = false;
    }

    private void rideAlong(Entity carrier) {
        AircraftEntity ac = this.vehicle;
        this.roll.set(this.roll.get() * 0.9F);
        ac.setXRot(ac.getXRot() * 0.95F);
        float targetYaw = Mth.wrapDegrees(carrier.getYRot() + 90.0F);
        if (targetYaw - ac.getYRot() > 180.0F) {
            targetYaw -= 360.0F;
        }
        if (targetYaw - ac.getYRot() < -180.0F) {
            targetYaw += 360.0F;
        }
        float maxTurn = 50.0F * (float) carrier.distanceToSqr(carrier.xo, carrier.yo, carrier.zo);
        if (maxTurn > 0.001) {
            maxTurn = Mth.sqrt(maxTurn);
            ac.setYRot(ac.getYRot() + LegacyMath.limit(targetYaw - ac.getYRot(), -maxTurn, maxTurn));
        }

        double x = ac.getX();
        double y = ac.getY();
        double z = ac.getZ();
        if (carrier.isRemoved()) {
            ac.stopRiding();
            this.remountDelay = REMOUNT_DELAY;
        } else if (this.engine.throttle() > JUMP_OFF_THROTTLE) {
            ac.setDeltaMovement(carrier.getDeltaMovement());
            ac.stopRiding();
            this.remountDelay = REMOUNT_DELAY;
        }
        ac.setPos(x, y, z);
    }

    /** Gets the vehicle off whatever carries it: a rack drops it at the rack's exit point. */
    public void leave() {
        AircraftEntity ac = this.vehicle;
        Vec3 target = ac.position();
        if (ac.getVehicle() instanceof SeatEntity seat) {
            AircraftEntity carrier = Objects.requireNonNull(seat.getParent());
            if (VehiclePeers.seats(carrier).info(ac) instanceof SeatRackInfo rack) {
                target = VehiclePeers.body(carrier).toWorld(rack.unmountPos != null ? rack.unmountPos : rack.getEntryPos());
            }
        } else if (ac.getVehicle() instanceof AbstractMinecart) {
            this.leftCartOnPurpose = true;
        }
        ac.moveTo(target.x, target.y, target.z, ac.getYRot(), ac.getXRot());
        ac.stopRiding();
        ac.moveTo(target.x, target.y, target.z, ac.getYRot(), ac.getXRot());
    }

    @Override
    public void describe(StateSink state) {
        Entity carrier = this.vehicle.getVehicle();
        state.put("carried by", carrier == null ? "-" : carrier.getName().getString());
        state.put("remount delay", this.remountDelay);
    }

    /** End of the tick: notices when the vehicle was loaded onto something. */
    public void rememberCarrier() {
        Entity carrier = this.vehicle.getVehicle();
        if (this.lastCarrier == null && carrier != null) {
            this.onLoaded.accept(carrier);
        }
        this.lastCarrier = carrier;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putBoolean(VehicleNbtCodec.DISMOUNTED_FROM_MINECART, this.leftCartOnPurpose);
    }

    @Override
    public void load(CompoundTag tag) {
        this.leftCartOnPurpose = tag.getBoolean(VehicleNbtCodec.DISMOUNTED_FROM_MINECART);
    }
}
