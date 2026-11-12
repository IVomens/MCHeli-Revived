package com.ivomens.mcheli.combat.projectile;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What clients are told about a projectile's flight.
 * <p>
 * Every client flies the projectiles it knows with the same code as the server (forces, steps, block
 * collisions), so for the kinds whose flight nothing else steers ({@link ProjectileKind#isFlownByClients},
 * rounds and rockets) the server does not send their position every other tick as vanilla would. A client
 * gets the projectile when it starts tracking it: where it is, its motion, and in the spawn packet's data
 * the ticks it has flown (so a rocket's motor burns on time) and whether the server's projectile is about
 * to fly one more tick before the packet leaves, which the client's copy then catches up. From there the
 * client flies it. The server sends the position and motion again (a teleport and a motion packet, at the
 * end of the projectile's tick, so they describe the projectile as it is when the tick's packets leave):
 * <ul>
 *   <li>when something only the server knows changed the flight: the launch boost from a moving
 *       carrier, a bounce, a stop at unloaded chunks ({@link #disturbed});</li>
 *   <li>every other tick while a client holds a motion the packets had to clamp (more than
 *       {@value #MAX_SENT_VELOCITY} blocks a tick on an axis, as a boosting rocket gets), until one that
 *       fits went out;</li>
 *   <li>every {@value #CORRECTION_INTERVAL} ticks of flight, which bounds the drift of the rounded motion
 *       of the packets (1/8000 of a block a tick).</li>
 * </ul>
 * Impacts, fuzes and bomblets end or split the projectile on the server; clients hear of that as removals
 * and new spawns, as before. {@code MixinServerEntity} keeps vanilla's tracker from sending moves for these
 * projectiles; changed entity data is still sent the vanilla way.
 */
public final class ProjectileSync {

    /** Ticks of flight between the corrections sent for a projectile clients fly themselves. */
    public static final int CORRECTION_INTERVAL = 40;
    /** Ticks between corrections while clients hold a clamped motion. */
    private static final int CLAMPED_INTERVAL = 2;
    /** Spawn and motion packets carry each axis of the motion up to this, in steps of 1/8000. */
    private static final double MAX_SENT_VELOCITY = 3.9;
    private static final long NEVER = Long.MIN_VALUE;

    private ProjectileSync() {
    }

    /** One projectile's sync state. */
    static final class State {

        /** Server: something changed the flight that clients cannot foresee. */
        private boolean disturbed;
        /** Server: some client was given a motion the packets clamped. */
        private boolean clampedSent;
        /** Server: the game time of the projectile's last tick. */
        private long lastTick = NEVER;
        /** Client: ticks the copy still has to fly to catch up with the server's. */
        private int catchUp;
    }

    /** The server's tracker of an entity, opened up by {@code MixinServerEntity}. */
    public interface Tracker {

        /** Takes the entity's current position, motion and rotation as what was sent last. */
        void mcheli$takeCurrentAsSent();
    }

    /** Whether clients fly this entity themselves, so {@code MixinServerEntity} sends no moves for it. */
    public static boolean isFlownByClients(Entity entity) {
        return entity instanceof ProjectileEntity projectile && projectile.kind().isFlownByClients();
    }

    /** Server: something clients cannot foresee changed the projectile's flight this tick. */
    static void disturbed(ProjectileEntity projectile) {
        if (!projectile.level().isClientSide) {
            projectile.sync.disturbed = true;
        }
    }

    /**
     * After every tick of the projectile. Server: sends the position and motion when they are due. Client:
     * flies the tick a copy that started in mid-flight still owes.
     */
    static void afterTick(ProjectileEntity projectile) {
        if (!projectile.kind().isFlownByClients() || projectile.isRemoved()) {
            return;
        }
        State state = projectile.sync;
        if (projectile.level() instanceof ServerLevel level) {
            state.lastTick = level.getGameTime();
            Vec3 motion = projectile.getDeltaMovement();
            if (state.disturbed || correctionDue(projectile.age(), motion, state.clampedSent)) {
                level.getChunkSource().broadcast(projectile, new ClientboundTeleportEntityPacket(projectile));
                level.getChunkSource().broadcast(projectile,
                        new ClientboundSetEntityMotionPacket(projectile.getId(), motion));
                state.clampedSent = !fitsPackets(motion);
            }
            state.disturbed = false;
        } else if (state.catchUp > 0) {
            state.catchUp--;
            projectile.setOldPosAndRot();
            projectile.tickCount++;
            projectile.fly();
        }
    }

    static boolean correctionDue(int ticksFlown, Vec3 motion, boolean clampedSent) {
        if (ticksFlown % CORRECTION_INTERVAL == 0) {
            return true;
        }
        return (clampedSent || !fitsPackets(motion)) && ticksFlown % CLAMPED_INTERVAL == 0;
    }

    /** Whether spawn and motion packets carry this motion without clamping it. */
    static boolean fitsPackets(Vec3 motion) {
        return Math.abs(motion.x) <= MAX_SENT_VELOCITY && Math.abs(motion.y) <= MAX_SENT_VELOCITY
                && Math.abs(motion.z) <= MAX_SENT_VELOCITY;
    }

    /**
     * The spawn packet for a player who starts tracking the projectile. One that clients fly themselves is
     * sent where it is now, with its motion now (the tracker takes that as sent; the spawn packet carries
     * the motion, and the tracker sends no motion packet with it). The data is the ticks flown, times two, plus one when
     * the projectile has flown before but not yet in this game tick: it will fly once more before the
     * packet leaves (the pairings of the tracker's update come before the entities tick).
     */
    static Packet<ClientGamePacketListener> spawnPacket(ProjectileEntity projectile, ServerEntity tracker) {
        if (!projectile.kind().isFlownByClients()) {
            return new ClientboundAddEntityPacket(projectile, tracker);
        }
        ((Tracker) tracker).mcheli$takeCurrentAsSent();
        State state = projectile.sync;
        if (!fitsPackets(projectile.getDeltaMovement())) {
            state.clampedSent = true;
        }
        boolean flyingOnThisTick = state.lastTick != NEVER && state.lastTick != projectile.level().getGameTime();
        return new ClientboundAddEntityPacket(projectile, tracker, projectile.age() * 2 + (flyingOnThisTick ? 1 : 0));
    }

    /**
     * Client: the motion the spawn packet carries (no motion packet follows it for these projectiles), and
     * from its data the ticks flown and the tick to catch up.
     */
    static void readSpawnPacket(ProjectileEntity projectile, ClientboundAddEntityPacket packet) {
        if (!projectile.kind().isFlownByClients()) {
            return;
        }
        projectile.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
        int data = packet.getData();
        if (data > 0) {
            projectile.setTicksFlown(data >> 1);
            projectile.sync.catchUp = data & 1;
        }
    }
}
