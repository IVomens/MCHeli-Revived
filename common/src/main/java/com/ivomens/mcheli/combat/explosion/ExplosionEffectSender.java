package com.ivomens.mcheli.combat.explosion;

import com.ivomens.mcheli.combat.explosion.net.ExplosionEffect;
import com.ivomens.mcheli.combat.explosion.net.ExplosionEffectsMessage;
import com.ivomens.mcheli.networking.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends the clients the explosions they are shown. The explosions of a tick are collected and go out at
 * its end, one {@link ExplosionEffectsMessage} per player with every explosion within {@link #RANGE} of
 * them, in the order they went off. The affected blocks only go to players whose client draws
 * explosions from them ({@link #setBlockDetail}). One per server ({@link #of}).
 */
public final class ExplosionEffectSender {

    /** Players further away than this do not get the effect. */
    public static final double RANGE = 1000;

    private final Map<ServerLevel, List<Pending>> pending = new LinkedHashMap<>();
    private final Set<UUID> blockDetail = ConcurrentHashMap.newKeySet();

    /** An explosion waiting for the end of the tick; {@code at} is its exact centre, for the range check. */
    private record Pending(Vec3 at, ExplosionEffect effect) {
    }

    /** Implemented on {@link MinecraftServer} by a mixin. */
    public interface Holder {
        ExplosionEffectSender mcheli$explosionEffects();
    }

    public static ExplosionEffectSender of(MinecraftServer server) {
        return ((Holder) server).mcheli$explosionEffects();
    }

    /** Whether the player's client draws explosions from their blocks, as it told the server. */
    public void setBlockDetail(Player player, boolean blocks) {
        if (blocks) {
            this.blockDetail.add(player.getUUID());
        } else {
            this.blockDetail.remove(player.getUUID());
        }
    }

    /** The player left; their next client tells the server again. */
    public void forget(Player player) {
        this.blockDetail.remove(player.getUUID());
    }

    /** Queues the explosion for the players around it; the blocks are kept only while anyone draws them. */
    void add(ServerLevel level, Vec3 at, float size, boolean inWater, List<BlockPos> affectedBlocks) {
        ExplosionEffect effect = ExplosionEffect.of(at, size, inWater, affectedBlocks, !this.blockDetail.isEmpty());
        this.pending.computeIfAbsent(level, key -> new ArrayList<>()).add(new Pending(at, effect));
    }

    /** End of the server tick: sends every player the explosions of the tick around them. */
    public void flush() {
        if (this.pending.isEmpty()) {
            return;
        }
        this.pending.forEach((level, effects) -> {
            List<ExplosionEffectsMessage> plain = null;
            for (ServerPlayer player : level.players()) {
                boolean blocks = this.blockDetail.contains(player.getUUID());
                List<ExplosionEffect> shown = inRange(player, effects, blocks);
                if (shown.size() == effects.size() && !blocks) {
                    // the common case: the same messages for everyone, built once
                    if (plain == null) {
                        plain = messages(shown);
                    }
                    plain.forEach(message -> Network.sendToPlayer(player, message));
                } else if (!shown.isEmpty()) {
                    messages(shown).forEach(message -> Network.sendToPlayer(player, message));
                }
            }
        });
        this.pending.clear();
    }

    /** The server stopped. */
    public void clear() {
        this.pending.clear();
        this.blockDetail.clear();
    }

    private static List<ExplosionEffect> inRange(ServerPlayer player, List<Pending> effects, boolean blocks) {
        double rangeSq = RANGE * RANGE;
        List<ExplosionEffect> shown = new ArrayList<>(effects.size());
        for (Pending pending : effects) {
            if (player.distanceToSqr(pending.at()) <= rangeSq) {
                shown.add(blocks ? pending.effect() : pending.effect().withoutBlocks());
            }
        }
        return shown;
    }

    /** The explosions in as few messages as their limits allow. */
    private static List<ExplosionEffectsMessage> messages(List<ExplosionEffect> effects) {
        List<ExplosionEffectsMessage> messages = new ArrayList<>(1);
        int from = 0;
        int blocks = 0;
        for (int i = 0; i < effects.size(); i++) {
            int size = effects.get(i).affectedBlocks().size();
            if (i - from == ExplosionEffectsMessage.MAX_EFFECTS || blocks + size > ExplosionEffectsMessage.MAX_TOTAL_BLOCKS) {
                messages.add(new ExplosionEffectsMessage(effects.subList(from, i)));
                from = i;
                blocks = 0;
            }
            blocks += size;
        }
        messages.add(new ExplosionEffectsMessage(effects.subList(from, effects.size())));
        return messages;
    }
}
