package com.ivomens.mcheli.gametest.golden;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.gametest.golden.PilotInput.Action;
import com.ivomens.mcheli.gametest.golden.PilotInput.Key;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A scripted drive: where the vehicle starts and what its pilot does on each tick.
 *
 * @param name       file-name part, lower case
 * @param ticks      how many ticks are recorded
 * @param spawn      the starting state
 * @param boardAt    tick on which the pilot starts climbing aboard, or -1 for an empty vehicle
 * @param negative   whether the run uses the course at negative coordinates
 * @param assist     makes the pilot's handling assists for one run, or null for a pilot without any
 */
public record Script(String name, int ticks, Spawn spawn, int boardAt, boolean negative,
                     List<Span> spans, List<Event> events, @Nullable Supplier<Assist> assist) {

    /** The client's handling assists, which turn the scripted input into what the client sends. */
    public interface Assist {
        PilotInput apply(AircraftEntity vehicle, PilotInput scripted);
    }

    public PilotInput inputAt(int tick) {
        PilotInput input = PilotInput.NONE;
        for (Span span : this.spans) {
            if (tick >= span.from() && tick < span.to()) {
                input = span.apply().apply(input);
            }
        }
        return input;
    }

    /** A copy of this script on the negative-coordinate course. */
    public Script atNegativeCoordinates() {
        return new Script(this.name + "_negative", this.ticks, this.spawn, this.boardAt, true, this.spans, this.events,
                this.assist);
    }

    public static Builder builder(String name, int ticks) {
        return new Builder(name, ticks);
    }

    /**
     * Starting state of the vehicle.
     *
     * @param position relative to the course origin (see {@link TestTerrain})
     * @param yaw      heading, 0 faces +z
     * @param throttle initial throttle, for vehicles started in flight
     * @param velocity initial velocity in blocks per tick
     * @param fuel     fuel at spawn, or -1 for a full tank
     */
    public record Spawn(Vec3 position, float yaw, double throttle, Vec3 velocity, int fuel) {

        public static Spawn at(Vec3 position) {
            return new Spawn(position, 0.0F, 0.0, Vec3.ZERO, -1);
        }

        public Spawn facing(float yaw) {
            return new Spawn(this.position, yaw, this.throttle, this.velocity, this.fuel);
        }

        public Spawn raised(double height) {
            return new Spawn(this.position.add(0.0, height, 0.0), this.yaw, this.throttle, this.velocity, this.fuel);
        }

        public Spawn flying(double throttle, double speed) {
            Vec3 forward = new Vec3(-Math.sin(Math.toRadians(this.yaw)), 0.0, Math.cos(Math.toRadians(this.yaw)));
            return new Spawn(this.position, this.yaw, throttle, forward.scale(speed), this.fuel);
        }

        public Spawn withFuel(int fuel) {
            return new Spawn(this.position, this.yaw, this.throttle, this.velocity, fuel);
        }
    }

    /** An input applied on every tick in {@code [from, to)}. */
    public record Span(int from, int to, UnaryOperator<PilotInput> apply) {
    }

    /** Something that happens to the vehicle from outside, at the start of a tick. */
    public record Event(int tick, String description, Consumer<AircraftEntity> action) {
    }

    public static final class Builder {
        private final String name;
        private final int ticks;
        private Spawn spawn = Spawn.at(TestTerrain.RUNWAY_START);
        private int boardAt = 0;
        private final List<Span> spans = new ArrayList<>();
        private final List<Event> events = new ArrayList<>();
        private Supplier<Assist> assist;

        private Builder(String name, int ticks) {
            this.name = name;
            this.ticks = ticks;
        }

        public Builder spawn(Spawn spawn) {
            this.spawn = spawn;
            return this;
        }

        /** Leaves the vehicle empty until the given tick. */
        public Builder boardAt(int tick) {
            this.boardAt = tick;
            return this;
        }

        public Builder unmanned() {
            this.boardAt = -1;
            return this;
        }

        public Builder hold(int from, int to, Key key) {
            this.spans.add(new Span(from, to, input -> input.withKey(key)));
            return this;
        }

        public Builder stick(int from, int to, float x, float y) {
            this.spans.add(new Span(from, to, input -> input.withStick(x, y)));
            return this;
        }

        public Builder press(int tick, Action action) {
            this.spans.add(new Span(tick, tick + 1, input -> input.withAction(action)));
            return this;
        }

        public Builder event(int tick, String description, Consumer<AircraftEntity> action) {
            this.events.add(new Event(tick, description, action));
            return this;
        }

        /** Flies with the handling assists the supplier makes, a fresh one for each run. */
        public Builder assisted(Supplier<Assist> pilotAssist) {
            this.assist = pilotAssist;
            return this;
        }

        public Script build() {
            return new Script(this.name, this.ticks, this.spawn, this.boardAt, false, List.copyOf(this.spans),
                    List.copyOf(this.events), this.assist);
        }
    }
}
