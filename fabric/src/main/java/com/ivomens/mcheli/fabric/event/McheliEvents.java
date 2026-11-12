package com.ivomens.mcheli.fabric.event;

import com.ivomens.mcheli.util.Ids;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Game events Fabric API has no counterpart for at the point where NeoForge fires its own. The mixins in
 * {@code fabric.mixin} fire them from the same place in the game code as NeoForge's patches do, so the
 * common hooks run at the same moment on both loaders. Listeners run in phase order: {@link #FIRST}
 * (NeoForge's highest priority), the default phase, then {@link #LAST} (its lowest).
 */
public final class McheliEvents {

    public static final ResourceLocation FIRST = Ids.of("first");
    public static final ResourceLocation LAST = Ids.of("last");

    /**
     * An entity is about to be added to a level, on either side: NeoForge's {@code EntityJoinLevelEvent}.
     * Nothing on Fabric can cancel the join, so the {@link #LAST} phase stands for "joined".
     */
    public static final Event<EntityLevelChange> ENTITY_JOIN = EventFactory.createWithPhases(EntityLevelChange.class,
            listeners -> (entity, level) -> {
                for (EntityLevelChange listener : listeners) {
                    listener.onLevelChange(entity, level);
                }
            }, FIRST, Event.DEFAULT_PHASE, LAST);

    /** An entity was removed from its level's tracking, on either side: NeoForge's {@code EntityLeaveLevelEvent}. */
    public static final Event<EntityLevelChange> ENTITY_LEAVE = EventFactory.createArrayBacked(EntityLevelChange.class,
            listeners -> (entity, level) -> {
                for (EntityLevelChange listener : listeners) {
                    listener.onLevelChange(entity, level);
                }
            });

    /**
     * A living entity is about to take damage, before armor and shields: NeoForge's
     * {@code LivingIncomingDamageEvent}. Each listener gets the amount the previous one returned; a NaN
     * amount cancels the hit and ends the chain.
     */
    public static final Event<IncomingDamage> LIVING_INCOMING_DAMAGE = EventFactory.createArrayBacked(IncomingDamage.class,
            listeners -> (entity, source, amount) -> {
                float result = amount;
                for (IncomingDamage listener : listeners) {
                    result = listener.onIncomingDamage(entity, source, result);
                    if (Float.isNaN(result)) {
                        break;
                    }
                }
                return result;
            });

    /**
     * A living entity is dying: NeoForge's {@code LivingDeathEvent}. Fabric mods prevent a death before
     * this point ({@code ServerLivingEntityEvents.ALLOW_DEATH}), so the {@link #LAST} phase stands for
     * "the death went ahead".
     */
    public static final Event<LivingDeath> LIVING_DEATH = EventFactory.createWithPhases(LivingDeath.class,
            listeners -> (entity, source) -> {
                for (LivingDeath listener : listeners) {
                    listener.onDeath(entity, source);
                }
            }, Event.DEFAULT_PHASE, LAST);

    /** Start of a player's tick, on either side: NeoForge's {@code PlayerTickEvent.Pre}. */
    public static final Event<PlayerTick> PLAYER_TICK_START = EventFactory.createWithPhases(PlayerTick.class,
            listeners -> player -> {
                for (PlayerTick listener : listeners) {
                    listener.onPlayerTick(player);
                }
            }, FIRST, Event.DEFAULT_PHASE);

    private McheliEvents() {
    }

    @FunctionalInterface
    public interface EntityLevelChange {
        void onLevelChange(Entity entity, Level level);
    }

    @FunctionalInterface
    public interface IncomingDamage {
        /** @return the damage to go on with, or NaN to cancel the hit */
        float onIncomingDamage(LivingEntity entity, DamageSource source, float amount);
    }

    @FunctionalInterface
    public interface LivingDeath {
        void onDeath(LivingEntity entity, DamageSource source);
    }

    @FunctionalInterface
    public interface PlayerTick {
        void onPlayerTick(Player player);
    }
}
