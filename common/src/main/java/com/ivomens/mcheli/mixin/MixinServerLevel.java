package com.ivomens.mcheli.mixin;

import com.ivomens.mcheli.admin.perf.TickCost;
import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.api.vehicle.Vehicle;
import com.ivomens.mcheli.combat.projectile.ProjectileEntity;
import com.ivomens.mcheli.event.ApiEvents;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every server level its own {@link TickCost} and times the tick of each MCHeli vehicle and
 * projectile in it, for {@code /mcheli perf}. Other entities only pay for two type checks.
 * <p>
 * Also tells the API's listeners about new vehicles ({@code VehicleSpawned}). Every way of placing an
 * entity (items, dispensers, commands, other mods) ends in {@code addFreshEntity}; loading a chunk or
 * changing dimension does not, so those are not reported as spawns.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements TickCost.Holder {

    @Unique
    private final TickCost mcheli$tickCost = new TickCost();

    @Override
    public TickCost mcheli$tickCost() {
        return this.mcheli$tickCost;
    }

    @WrapMethod(method = "tickNonPassenger")
    private void mcheli$timeTick(Entity entity, Operation<Void> original) {
        TickCost.Kind kind = entity instanceof AircraftEntity ? TickCost.Kind.VEHICLE
                : entity instanceof ProjectileEntity ? TickCost.Kind.PROJECTILE : null;
        if (kind == null) {
            original.call(entity);
            return;
        }
        long start = TickCost.now();
        original.call(entity);
        this.mcheli$tickCost.record(entity, kind, ((ServerLevel) (Object) this).getGameTime(), start);
    }

    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void mcheli$announceSpawnedVehicle(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity instanceof Vehicle vehicle) {
            ApiEvents.vehicleSpawned(vehicle);
        }
    }
}
