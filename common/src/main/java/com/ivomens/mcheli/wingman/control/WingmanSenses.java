package com.ivomens.mcheli.wingman.control;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.networking.Network;
import com.ivomens.mcheli.core.wingman.brain.Point;
import com.ivomens.mcheli.core.wingman.brain.Senses;
import com.ivomens.mcheli.wingman.net.AutopilotVisualMessage;
import com.ivomens.mcheli.wingman.roster.Wingman;
import com.ivomens.mcheli.wingman.text.WingmanText;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** {@link Senses} of one wingman, answered from its level, the roster and the combat state. */
final class WingmanSenses implements Senses {

    private static final double RECON_REPORT_RANGE = 500.0;

    private final ServerLevel level;
    private final AircraftEntity aircraft;
    private final Wingman wingman;
    private final WingmanCombat combat;
    private final AutopilotRiders riders;

    WingmanSenses(ServerLevel level, AircraftEntity aircraft, Wingman wingman, WingmanCombat combat,
                  AutopilotRiders riders) {
        this.level = level;
        this.aircraft = aircraft;
        this.wingman = wingman;
        this.combat = combat;
        this.riders = riders;
    }

    @Override
    public List<Point> siblings() {
        List<Point> points = new ArrayList<>();
        for (Entity sibling : this.combat.siblings(this.aircraft.getUUID(), this.wingman.leader(), this.level)) {
            points.add(new Point(sibling.getX(), sibling.getY(), sibling.getZ()));
        }
        return points;
    }

    @Override
    public boolean consumeEscapeTick() {
        return this.combat.consumeEscapeTick(this.aircraft.getUUID());
    }

    @Override
    public Point attackTarget() {
        Entity target = this.combat.resolveTarget(this.level, this.aircraft, this.wingman);
        return target == null || target.isRemoved() ? null : new Point(target.getX(), target.getY(), target.getZ());
    }

    @Override
    public double groundHeight(int x, int z) {
        return groundHeight(this.level, x, z);
    }

    /**
     * First free block above the motion-blocking surface at x/z. Only loaded chunks are sampled; an
     * unloaded column reports the bottom of the world instead of loading it.
     */
    static double groundHeight(Level level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return level.getMinBuildHeight();
        }
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
    }

    @Override
    public boolean hostileWithin(double radius) {
        double radiusSq = radius * radius;
        for (Entity e : this.level.getEntities(this.aircraft, this.aircraft.getBoundingBox().inflate(radius),
                en -> en instanceof Enemy && en.isAlive())) {
            if (this.aircraft.distanceToSqr(e) < radiusSq) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hostilesAround(double x, double z, double radius) {
        AABB box = new AABB(x - radius, this.level.getMinBuildHeight(), z - radius, x + radius,
                this.level.getMaxBuildHeight(), z + radius);
        int count = 0;
        for (Entity e : this.level.getEntities((Entity) null, box, en -> en instanceof Enemy && en.isAlive())) {
            double dx = e.getX() - x;
            double dz = e.getZ() - z;
            if (dx * dx + dz * dz < radius * radius) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void showAutopilot(float yaw, float throttle) {
        if (AircraftControls.pilotOf(this.aircraft) instanceof ServerPlayer rider) {
            Network.sendToPlayer(rider, new AutopilotVisualMessage(this.aircraft.getId(), yaw, throttle));
            this.riders.track(this.aircraft.getId(), rider);
        }
    }

    @Override
    public void keepAutopilotShown() {
        this.riders.markActive(this.aircraft.getId());
    }

    @Override
    public void reportRecon(int hostiles, String reason) {
        var report = WingmanText.reconReport(shortId(this.aircraft), hostiles, reason);
        for (Player player : this.aircraft.level().players()) {
            if (player.distanceTo(this.aircraft) < RECON_REPORT_RANGE) {
                player.sendSystemMessage(report);
            }
        }
    }

    /** First eight characters of the entity UUID, for log lines and reports. */
    static String shortId(Entity e) {
        return e.getUUID().toString().substring(0, 8);
    }
}
