package com.ivomens.mcheli.mixin;

import com.ivomens.mcheli.combat.explosion.ExplosionEffectSender;
import com.ivomens.mcheli.livery.LiveryManager;
import com.ivomens.mcheli.multiplay.OperatorRequests;
import com.ivomens.mcheli.radar.RadarContactSync;
import com.ivomens.mcheli.vehicle.sensor.ThreatBoard;
import com.ivomens.mcheli.vehicledata.VehicleDataSync;
import com.ivomens.mcheli.wingman.WingmanService;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every server its own {@link ThreatBoard}, {@link WingmanService}, {@link LiveryManager},
 * {@link OperatorRequests}, {@link RadarContactSync}, {@link ExplosionEffectSender} and {@link VehicleDataSync}, so
 * radar warnings, wingmen, livery uploads, operator requests, radar contacts, explosion effects and addon values
 * waiting to be synced never outlive the world they came from.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements ThreatBoard.Holder, WingmanService.Holder, LiveryManager.Holder,
        OperatorRequests.Holder, RadarContactSync.Holder, ExplosionEffectSender.Holder, VehicleDataSync.Holder {

    @Unique
    private final ThreatBoard mcheli$threatBoard = new ThreatBoard((MinecraftServer) (Object) this);

    @Unique
    private final WingmanService mcheli$wingmen = new WingmanService();

    @Unique
    @Nullable
    private LiveryManager mcheli$liveries;

    @Unique
    private final OperatorRequests mcheli$operatorRequests = new OperatorRequests();

    @Unique
    private final RadarContactSync mcheli$radarContacts = new RadarContactSync();

    @Unique
    private final ExplosionEffectSender mcheli$explosionEffects = new ExplosionEffectSender();

    @Unique
    private final VehicleDataSync mcheli$vehicleDataSync = new VehicleDataSync();

    @Override
    public ThreatBoard mcheli$threatBoard() {
        return this.mcheli$threatBoard;
    }

    @Override
    public WingmanService mcheli$wingmen() {
        return this.mcheli$wingmen;
    }

    @Override
    @Nullable
    public LiveryManager mcheli$liveries() {
        return this.mcheli$liveries;
    }

    @Override
    public OperatorRequests mcheli$operatorRequests() {
        return this.mcheli$operatorRequests;
    }

    @Override
    public RadarContactSync mcheli$radarContacts() {
        return this.mcheli$radarContacts;
    }

    @Override
    public ExplosionEffectSender mcheli$explosionEffects() {
        return this.mcheli$explosionEffects;
    }

    @Override
    public VehicleDataSync mcheli$vehicleDataSync() {
        return this.mcheli$vehicleDataSync;
    }

    @Override
    public void mcheli$setLiveries(@Nullable LiveryManager liveries) {
        this.mcheli$liveries = liveries;
    }
}
