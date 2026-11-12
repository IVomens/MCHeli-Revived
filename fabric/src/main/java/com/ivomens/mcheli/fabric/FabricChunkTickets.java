package com.ivomens.mcheli.fabric;

import com.ivomens.mcheli.platform.ChunkTickets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Vanilla region tickets, one ticket type per set. A ticket keeps its chunk entity-ticking at the same
 * level as NeoForge's forced chunks (two below a full chunk), and the chunk is loaded right away when it
 * is first forced, as NeoForge does.
 * <p>
 * Two differences from NeoForge's ticket controllers: vanilla region tickets are never saved, which is
 * what MCHeli wants anyway (its tickets are rebuilt at runtime); and vanilla has no "force ticks" flag,
 * so a chunk forced with {@code ticking} gets entity ticks but, with no player near, no random ticks or
 * mob spawning.
 */
public class FabricChunkTickets implements ChunkTickets {

    /** Ticket level offset NeoForge uses for forced chunks: entity-ticking. */
    private static final int DISTANCE = 2;
    private static final Comparator<Owner> OWNER_ORDER = Comparator.comparing(Owner::id).thenComparing(Owner::ticking);

    @Override
    public TicketSet create(ResourceLocation id) {
        return new VanillaTickets(TicketType.create(id.toString(), OWNER_ORDER));
    }

    /** The ticket's key: who forced the chunk, and how. */
    private record Owner(UUID id, boolean ticking) {
    }

    /** Ticket type plus the tickets each level holds, so a second force or release by an owner is a no-op. */
    private static final class VanillaTickets implements TicketSet {

        private final TicketType<Owner> type;
        private final Map<ServerLevel, Set<Held>> held = Collections.synchronizedMap(new WeakHashMap<>());

        VanillaTickets(TicketType<Owner> type) {
            this.type = type;
        }

        @Override
        public void force(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean ticking) {
            Held ticket = new Held(owner, ChunkPos.asLong(chunkX, chunkZ), ticking);
            if (this.held.computeIfAbsent(level, key -> new HashSet<>()).add(ticket)) {
                level.getChunkSource().addRegionTicket(this.type, new ChunkPos(chunkX, chunkZ), DISTANCE, new Owner(owner, ticking));
                level.getChunk(chunkX, chunkZ);
            }
        }

        @Override
        public void release(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean ticking) {
            Set<Held> tickets = this.held.get(level);
            if (tickets != null && tickets.remove(new Held(owner, ChunkPos.asLong(chunkX, chunkZ), ticking))) {
                level.getChunkSource().removeRegionTicket(this.type, new ChunkPos(chunkX, chunkZ), DISTANCE, new Owner(owner, ticking));
            }
        }
    }

    private record Held(UUID owner, long chunk, boolean ticking) {
    }
}
