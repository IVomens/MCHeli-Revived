package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.api.event.CountermeasureType;
import com.ivomens.mcheli.api.vehicle.Targetable;
import com.ivomens.mcheli.event.ApiEvents;
import com.ivomens.mcheli.vehicle.component.countermeasure.ActiveProtection;
import com.ivomens.mcheli.vehicle.component.countermeasure.ChaffDispenser;
import com.ivomens.mcheli.vehicle.component.countermeasure.FlareDispenser;
import com.ivomens.mcheli.vehicle.component.countermeasure.IronCurtain;
import com.ivomens.mcheli.vehicle.component.countermeasure.MissileWarning;

/**
 * Everything a vehicle does against missiles: flares, chaff, the active protection system or the iron
 * curtain, the ECM jammer and the missile warning. The countermeasures themselves live in
 * {@code combat.countermeasure}; this component fits them to the vehicle's current definition.
 * <p>
 * The dispensers tick in {@link #serverTick}/{@link #clientTick}; the missile warning ticks
 * separately, earlier in the tick, see the pipeline.
 */
@TickOrder(phase = TickPhase.SYSTEMS)
public final class CountermeasureComponent implements VehicleComponent, Targetable {

    /** ECM jammer type that breaks radar missile locks while it runs. */
    private static final int LOCK_BREAKING_JAMMER = 2;

    private final AircraftEntity vehicle;
    private final StatusWord status;
    private final FlareDispenser flares;
    private final ChaffDispenser chaff;
    private final ActiveProtection activeProtection;
    private final IronCurtain ironCurtain;
    private final MissileWarning missileWarning;

    private int ecmJammerRunningTicks;
    private int ecmJammerWaitTicks;
    /** While positive, radar warning receivers cannot see this vehicle. */
    private int jammingTicks;
    /** Last chaff burst length the server announced; radar missiles lose their lock while positive. */
    private int announcedChaffTime;
    private int currentFlareIndex;

    public CountermeasureComponent(AircraftEntity vehicle, StatusWord status, SeatsComponent seats, BodyComponent body) {
        this.vehicle = vehicle;
        this.status = status;
        this.flares = new FlareDispenser(vehicle, body);
        this.chaff = new ChaffDispenser(vehicle);
        this.activeProtection = new ActiveProtection(vehicle);
        this.ironCurtain = new IronCurtain(vehicle);
        this.missileWarning = new MissileWarning(vehicle, vehicle.level(), this, seats);
    }

    @Override
    public void serverTick(TickContext ctx) {
        boolean flaresRechargingBefore = this.flares.isRecharging();
        tickDispensers();
        if (this.ecmJammerRunningTicks > 0 && --this.ecmJammerRunningTicks <= 0) {
            this.status.set(StatusWord.ECM_JAMMER, false, true);
        }
        tickCooldowns();
        if (!this.flares.isRecharging() && flaresRechargingBefore) {
            this.status.set(StatusWord.FLARE, false);
        }
    }

    @Override
    public void clientTick(TickContext ctx) {
        tickDispensers();
        if (this.ecmJammerRunningTicks > 0) {
            this.ecmJammerRunningTicks--;
        }
        tickCooldowns();
    }

    private void tickDispensers() {
        this.flares.tick();
        VehicleSpec info = this.vehicle.spec();
        if (info != null) {
            this.chaff.tick();
            if (info.file().features().aps().enabled() && isIronCurtain(info)) {
                this.ironCurtain.tick();
            } else if (info.file().features().aps().enabled()) {
                this.activeProtection.setRange(info.file().features().aps().range());
                this.activeProtection.tick();
            }
        }
        this.ironCurtain.tickShimmer();
    }

    private static boolean isIronCurtain(VehicleSpec info) {
        return info.file().features().aps().isIronCurtain();
    }

    private void tickCooldowns() {
        if (this.ecmJammerWaitTicks > 0) {
            this.ecmJammerWaitTicks--;
        }
        if (this.jammingTicks > 0) {
            this.jammingTicks--;
        }
    }

    /** The missile warning; runs earlier in the tick than the dispensers. */
    public void updateMissileWarning() {
        this.missileWarning.update();
    }

    public boolean isLockedByMissile() {
        return this.missileWarning.isLockedByMissile();
    }

    public IronCurtain ironCurtain() {
        return this.ironCurtain;
    }

    @Override
    public void describe(StateSink state) {
        state.put("locked by missile", isLockedByMissile());
        state.put("flare using", isFlareUsing());
        state.put("flare cooldown", this.flares.cooldown());
        state.put("chaff using", isChaffUsing());
        state.put("ecm running ticks", this.ecmJammerRunningTicks);
        state.put("ecm wait ticks", this.ecmJammerWaitTicks);
        state.put("jamming ticks", this.jammingTicks);
        state.put("iron curtain", isIronCurtainRunning());
    }

    public int jammingTicks() {
        return this.jammingTicks;
    }

    @Override
    public boolean isIronCurtainRunning() {
        return this.ironCurtain.isRunning();
    }

    @Override
    public boolean isBreakingRadarLocks() {
        VehicleSpec info = info();
        return info != null && info.file().features().ecm().type() == LOCK_BREAKING_JAMMER && isECMJammerUsing();
    }

    @Override
    public int announcedChaffTime() {
        return this.announcedChaffTime;
    }

    public void announceChaff(int ticks) {
        this.announcedChaffTime = ticks;
    }

    private VehicleSpec info() {
        return this.vehicle.spec();
    }

    /** How hard the vehicle is to lock on to, 0 for not at all. */
    @Override
    public float stealth() {
        VehicleSpec info = info();
        return info != null ? info.file().stealth() : 0.0F;
    }

    public boolean haveFlare() {
        return info() != null && info().armament().haveFlare();
    }

    public boolean useFlare(int type) {
        VehicleSpec info = info();
        if (info == null || !info.armament().haveFlare()) {
            return false;
        }
        for (int flareType : info.armament().flare().types) {
            if (flareType == type) {
                this.status.set(StatusWord.FLARE, true);
                if (this.flares.release(type)) {
                    return deployed(CountermeasureType.FLARE);
                }
            }
        }
        return false;
    }

    public int currentFlareType() {
        return haveFlare() ? info().armament().flare().types[this.currentFlareIndex] : 0;
    }

    public void nextFlareType() {
        if (haveFlare()) {
            this.currentFlareIndex = (this.currentFlareIndex + 1) % info().armament().flare().types.length;
        }
    }

    public boolean canUseFlare() {
        return haveFlare() && !this.status.get(StatusWord.FLARE) && !this.flares.isRecharging();
    }

    public boolean isFlarePreparation() {
        return this.flares.isRecharging();
    }

    @Override
    public boolean isFlareUsing() {
        return this.flares.isActive();
    }

    public int flareTick() {
        return this.flares.cooldown();
    }

    public boolean haveChaff() {
        return info() != null && info().armament().haveChaff();
    }

    public boolean canUseChaff() {
        return info() != null && info().armament().haveChaff() && !this.chaff.isRecharging();
    }

    public boolean useChaff() {
        VehicleSpec info = info();
        return info != null && info.armament().haveChaff()
                && this.chaff.release(info.file().features().flare().chaffUseTime(),
                info.file().features().flare().chaffWaitTime())
                && deployed(CountermeasureType.CHAFF);
    }

    public boolean isChaffUsing() {
        return this.chaff.isActive();
    }

    public boolean haveAPS() {
        return info() != null && info().file().features().aps().enabled();
    }

    public boolean useAPS() {
        if (!haveAPS()) {
            return false;
        }
        VehicleSpec info = info();
        return isIronCurtain(info)
                ? this.ironCurtain.activate(info.file().features().aps().useTime(),
                info.file().features().aps().waitTime()) && deployed(CountermeasureType.IRON_CURTAIN)
                : this.activeProtection.activate(info.file().features().aps().useTime(),
                        info.file().features().aps().waitTime()) && deployed(CountermeasureType.ACTIVE_PROTECTION);
    }

    public boolean canUseAPS() {
        return haveAPS() && !(isIronCurtain(info()) ? this.ironCurtain : this.activeProtection).isRecharging();
    }

    public boolean isECMJammerUsing() {
        return this.status.get(StatusWord.ECM_JAMMER);
    }

    public void setECMJammerUsing(boolean enabled) {
        this.status.set(StatusWord.ECM_JAMMER, enabled, true);
    }

    /** Starts the jammer if it is fitted and rested; only the server publishes that it runs. */
    public boolean useECMJammer() {
        VehicleSpec info = info();
        if (info == null || !info.file().features().ecm().enabled() || this.ecmJammerWaitTicks > 0) {
            return false;
        }
        this.ecmJammerRunningTicks = info.file().features().ecm().useTime();
        this.ecmJammerWaitTicks = info.file().features().ecm().waitTime();
        this.status.set(StatusWord.ECM_JAMMER, true);
        return deployed(CountermeasureType.ECM_JAMMER);
    }

    /** Tells the API's listeners on the server that a countermeasure started; always true. */
    private boolean deployed(CountermeasureType type) {
        if (!this.vehicle.level().isClientSide) {
            ApiEvents.countermeasureDeployed(this.vehicle, type);
        }
        return true;
    }
}
