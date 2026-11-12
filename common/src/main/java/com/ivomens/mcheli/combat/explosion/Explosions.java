package com.ivomens.mcheli.combat.explosion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Sets explosions off on the server and queues their effect for the clients nearby ({@link ExplosionEffectSender}). */
public final class Explosions {

    /** Watchers of every server explosion; none in normal play, tests add one to record explosions. */
    private static final List<Consumer<Detonation>> LISTENERS = new CopyOnWriteArrayList<>();

    private Explosions() {
    }

    /** An explosion as it went off, for {@link #addListener listeners}. */
    public record Detonation(@Nullable Entity exploder, ExplosionSpec spec) {
    }

    public static void addListener(Consumer<Detonation> listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Consumer<Detonation> listener) {
        LISTENERS.remove(listener);
    }

    /**
     * Sets the explosion off. Blocks only break where mob griefing is on.
     *
     * @param exploder the entity that blew up (its own blast does not hit it), or null
     * @param placedBy whoever caused it, credited with the damage, or null
     * @return what it hit; {@link ExplosionResult#NONE} on the client, which does not run explosions
     */
    public static ExplosionResult detonate(Level level, ExplosionSpec spec, @Nullable Entity exploder,
                                           @Nullable Entity placedBy) {
        if (level.isClientSide()) {
            return ExplosionResult.NONE;
        }
        if (!LISTENERS.isEmpty()) {
            Detonation detonation = new Detonation(exploder, spec);
            LISTENERS.forEach(listener -> listener.accept(detonation));
        }
        boolean damagesTerrain = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        McheliExplosion explosion = new McheliExplosion(level, exploder, placedBy, spec, damagesTerrain);
        ExplosionResult result = explosion.detonate();
        if (level instanceof ServerLevel serverLevel) {
            ExplosionEffectSender.of(serverLevel.getServer()).add(serverLevel, spec.at(), spec.size(), spec.inWater(),
                    explosion.getAffectedBlockPositions());
        }
        return result;
    }
}
