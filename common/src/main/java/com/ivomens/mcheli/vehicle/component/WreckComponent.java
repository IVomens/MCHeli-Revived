package com.ivomens.mcheli.vehicle.component;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.DummyCommandSender;
import com.ivomens.mcheli.aircraft.SeatEntity;
import com.ivomens.mcheli.combat.damage.DamageScaling;
import com.ivomens.mcheli.combat.explosion.Explosions;
import com.ivomens.mcheli.vehicle.settings.DurabilityRules;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

/**
 * The end of a vehicle: a target drone blows up when out of fuel, a wreck burns its crew and counts
 * down to despawning. A vehicle is a wreck while its despawn countdown runs.
 */
@TickOrder(phase = TickPhase.LIFECYCLE)
public final class WreckComponent implements VehicleComponent {

    private static final int BURN_INTERVAL = 20;
    private static final int BURN_SECONDS = 5;
    /** A target drone only checks its fuel once it has been in the world for a second. */
    private static final int DRONE_FUEL_CHECK_AGE = 20;

    private final AircraftEntity vehicle;
    /** Command a server operator set to run when the vehicle is destroyed. */
    private final SyncedValue<String> command;
    private final DamageComponent damage;
    private final EngineComponent engine;
    private final LifecycleComponent lifecycle;
    private final SeatsComponent seats;
    private final EjectionSeats ejectionSeats;
    private final StationShutdown stations;
    private final Supplier<DurabilityRules> durability;
    private WreckMotion motion = WreckMotion.STILL;
    private int despawnCount;
    private float tumbleYaw;
    private float tumblePitch;
    private float tumbleRoll;

    public WreckComponent(AircraftEntity vehicle, SyncedValue<String> command, DamageComponent damage,
            EngineComponent engine, LifecycleComponent lifecycle, SeatsComponent seats, EjectionSeats ejectionSeats,
            StationShutdown stations, Supplier<DurabilityRules> durability) {
        this.vehicle = vehicle;
        this.command = command;
        this.damage = damage;
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.seats = seats;
        this.ejectionSeats = ejectionSeats;
        this.stations = stations;
        this.durability = durability;
        damage.whenDestroyed(this::destroyByDamage);
    }

    public void setMotion(WreckMotion motion) {
        this.motion = motion;
    }

    public String command() {
        return this.command.get();
    }

    /** Sets the command run when the vehicle is destroyed, as a player asked; nothing without a player. */
    public void setCommand(String command, @Nullable Player player) {
        if (player != null) {
            setCommand(command);
        }
    }

    /** Server: sets the command without asking who wants it, as loading a save or an item does. */
    public void setCommand(String command) {
        this.command.set(command);
    }

    /** Server: hits destroyed the vehicle. It becomes a wreck, and the operator's command runs. */
    private void destroyByDamage() {
        destroy();
        String command = command().trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (!command.isEmpty()) {
            DummyCommandSender.execCommand(command);
        }
    }

    /**
     * Server: turns the vehicle into a burning wreck. Modes go off, the despawn timer starts, and the
     * crew is ejected where possible; everyone still inside is hurt (killed with
     * KillPassengersWhenDestroyed).
     */
    public void destroy() {
        AircraftEntity ac = this.vehicle;
        catchFire();
        this.ejectionSeats.ejectSeat(ac.getRiddenByEntity());
        Entity copilot = this.seats.riderOf(1);
        if (copilot != null) {
            this.ejectionSeats.ejectSeat(copilot);
        }
        hurtCrew();
        this.motion.adjustTumble(this, ac, ac.getRandom());
    }

    /** What both sides do when the vehicle is destroyed: a client sees it by the vehicle's HP. */
    private void catchFire() {
        AircraftEntity ac = this.vehicle;
        this.stations.shutDown();

        int despawnTicks = 20 * this.durability.get().wreckSeconds();
        setDespawnCount(ac.isTargetDrone() ? despawnTicks / 10 : despawnTicks);
        this.tumblePitch = ac.getRandom().nextFloat() - 0.5F;
        this.tumbleRoll = (ac.getRandom().nextFloat() - 0.5F) * 0.5F;
        this.tumbleYaw = 0.0F;
        if (ac.isUAV() && ac.getRiddenByEntity() != null) {
            ac.getRiddenByEntity().stopRiding();
        }
    }

    public float tumbleYaw() {
        return this.tumbleYaw;
    }

    public float tumblePitch() {
        return this.tumblePitch;
    }

    public float tumbleRoll() {
        return this.tumbleRoll;
    }

    public void setTumbleYaw(float degreesPerTick) {
        this.tumbleYaw = degreesPerTick;
    }

    public void setTumbleRoll(float degreesPerTick) {
        this.tumbleRoll = degreesPerTick;
    }

    /** Client: the wreck tumbles the way its vehicle type does. */
    public void tickMotion() {
        if (isWreck()) {
            this.motion.tick(this, this.vehicle);
        }
    }

    @Override
    public void describe(StateSink state) {
        state.put("wreck", isWreck());
        state.put("despawn count", this.despawnCount);
        state.put("tumbling", this.motion != WreckMotion.STILL);
        state.put("command", command().isEmpty() ? "-" : command());
    }

    public boolean isWreck() {
        return this.despawnCount > 0;
    }

    public int despawnCount() {
        return this.despawnCount;
    }

    public void setDespawnCount(int ticks) {
        this.despawnCount = ticks;
    }

    @Override
    public void serverTick(TickContext ctx) {
        AircraftEntity ac = this.vehicle;
        if (ac.isTargetDrone() && !isWreck() && this.lifecycle.age() > DRONE_FUEL_CHECK_AGE && !this.engine.canRun()) {
            this.damage.setDamageTaken(this.damage.maxHp());
            destroy();
            Explosions.detonate(ac.level(), DamageComponent.wreckExplosion(ac, 2.0F, 2.0F), null, null);
        }
        if (this.despawnCount > 0) {
            this.despawnCount--;
            if (this.despawnCount <= 1) {
                this.lifecycle.destroy(true);
            }
        }
    }

    /** The server's despawn countdown is not synced; a client sees the wreck by its HP. */
    @Override
    public void clientTick(TickContext ctx) {
        AircraftEntity ac = this.vehicle;
        if (ac.spec() != null && this.damage.hp() <= 0 && this.despawnCount <= 0) {
            catchFire();
            this.motion.adjustTumble(this, ac, ac.getRandom());
        }
    }

    /** Everyone still inside a burning wreck catches fire once a second (a UAV operator is not inside). */
    public void burnCrew() {
        AircraftEntity ac = this.vehicle;
        if (!isWreck() || this.lifecycle.age() % BURN_INTERVAL != 0) {
            return;
        }
        for (int seatId = 0; seatId < this.seats.count() + 1; seatId++) {
            Entity entity = this.seats.riderOf(seatId);
            if (entity != null && (seatId != 0 || !ac.isUAV())
                    && DamageScaling.vsEntity(entity, ac.damageSources().inFire(), 1.0F) > 0.0F) {
                entity.igniteForSeconds(BURN_SECONDS);
            }
        }
    }

    /** The crew still aboard when the vehicle is destroyed get hurt (or killed, by config). */
    private void hurtCrew() {
        AircraftEntity ac = this.vehicle;
        float damage = this.durability.get().killCrewWhenDestroyed() ? 100000.0F : 0.001F;
        Entity attacker = this.damage.lastAttacker();
        DamageSource source = ac.damageSources().generic();
        if (ac.level().getDifficulty() != Difficulty.PEACEFUL) {
            source = ac.damageSources().explosion(ac, attacker);
        } else if (attacker instanceof Player player) {
            source = ac.damageSources().playerAttack(player);
        }

        Entity pilot = ac.getRiddenByEntity();
        if (pilot != null) {
            pilot.hurt(source, damage);
        }
        for (SeatEntity seat : this.seats.all()) {
            if (seat != null && seat.getRiddenByEntity() != null) {
                seat.getRiddenByEntity().hurt(source, damage);
            }
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt(VehicleNbtCodec.DESPAWN_COUNT, this.despawnCount);
    }

    @Override
    public void load(CompoundTag tag) {
        this.despawnCount = tag.getInt(VehicleNbtCodec.DESPAWN_COUNT);
    }
}
