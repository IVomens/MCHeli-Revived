package com.ivomens.mcheli.core.sim.model;

import com.ivomens.mcheli.core.sim.body.BodyState;
import com.ivomens.mcheli.core.sim.body.MoveResult;
import com.ivomens.mcheli.core.sim.control.ControlInput;
import com.ivomens.mcheli.core.sim.math.Attitude;
import com.ivomens.mcheli.core.sim.math.Vec3d;
import com.ivomens.mcheli.core.sim.port.Collider;
import com.ivomens.mcheli.core.sim.port.SimRandom;
import com.ivomens.mcheli.core.sim.port.StepContext;
import com.ivomens.mcheli.core.sim.port.TerrainProbe;
import com.ivomens.mcheli.core.sim.propulsion.PropulsionState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Hand-made worlds for the motion model tests: open sky, flat ground, water, fixed random draws. */
final class SimFakes {

    static final VerticalForces.Gravity GRAVITY = new VerticalForces.Gravity(-0.04F, -0.04F);
    static final ControlInput NO_KEYS = new ControlInput(false, false, false, false, false);
    static final Attitude LEVEL = new Attitude(0.0F, 0.0F, 0.0F);

    private SimFakes() {
    }

    static BodyState airborne(Vec3d velocity) {
        return new BodyState(velocity, LEVEL, false, false, false);
    }

    static BodyState onGround(Vec3d velocity) {
        return new BodyState(velocity, LEVEL, true, false, false);
    }

    static PropulsionState throttle(double throttle) {
        return new PropulsionState(throttle, 0.0F, 0.07, 0.0F);
    }

    static StepContext context(TerrainProbe terrain, Collider collider) {
        return new StepContext(terrain, collider, new ScriptedRandom());
    }

    /** Terrain with a fixed answer to every question. */
    record FixedTerrain(boolean clearBelow, double waterDepth, boolean aboveWaterOrAir) implements TerrainProbe {

        static final FixedTerrain OPEN_SKY = new FixedTerrain(true, 0.0, true);
        static final FixedTerrain SOLID_GROUND = new FixedTerrain(false, 0.0, false);

        static FixedTerrain water(double depth) {
            return new FixedTerrain(false, depth, true);
        }

        @Override
        public boolean isClearBelow(int size, int depth) {
            return this.clearBelow;
        }

        @Override
        public double waterDepth() {
            return this.waterDepth;
        }

        @Override
        public boolean isAboveWaterOrAir() {
            return this.aboveWaterOrAir;
        }
    }

    /** Terrain whose ground lies a given number of blocks below. */
    record GroundBelow(int blocks) implements TerrainProbe {

        @Override
        public boolean isClearBelow(int size, int depth) {
            return depth < this.blocks;
        }

        @Override
        public double waterDepth() {
            return 0.0;
        }

        @Override
        public boolean isAboveWaterOrAir() {
            return this.blocks > 1;
        }
    }

    /**
     * Moves without obstacles, except that a body resting on the ground cannot sink into it. Records
     * every body it moves.
     */
    static final class RecordingCollider implements Collider {

        private final List<BodyState> moved = new ArrayList<>();

        @Override
        public MoveResult move(BodyState body) {
            this.moved.add(body);
            Vec3d velocity = body.velocity();
            if (body.onGround() && velocity.y() <= 0.0) {
                return new MoveResult(velocity.withY(0.0), true, false);
            }
            return new MoveResult(velocity, false, false);
        }

        BodyState last() {
            return this.moved.get(this.moved.size() - 1);
        }
    }

    /** Hands out queued numbers, then ones and halves, which never trigger a drift; counts the draws. */
    static final class ScriptedRandom implements SimRandom {

        private final Deque<Integer> ints = new ArrayDeque<>();
        private final Deque<Double> doubles = new ArrayDeque<>();
        private int draws;

        ScriptedRandom ints(Integer... values) {
            this.ints.addAll(List.of(values));
            return this;
        }

        ScriptedRandom doubles(Double... values) {
            this.doubles.addAll(List.of(values));
            return this;
        }

        int draws() {
            return this.draws;
        }

        @Override
        public int nextInt(int bound) {
            this.draws++;
            return this.ints.isEmpty() ? 1 : this.ints.poll();
        }

        @Override
        public double nextDouble() {
            this.draws++;
            return this.doubles.isEmpty() ? 0.5 : this.doubles.poll();
        }
    }
}
