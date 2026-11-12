package com.ivomens.mcheli.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Forced-chunk tickets. A ticket keeps its chunk loaded until it is released. Tickets are never
 * restored after a restart: anything still forced when the server stopped is dropped on the next load.
 */
public interface ChunkTickets {

    /** A named set of tickets. Create it during mod init, before the loader opens its ticket registry. */
    TicketSet create(ResourceLocation id);

    interface TicketSet {

        /** No-op when the owner already holds a ticket with the same {@code ticking} for that chunk. */
        void force(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean ticking);

        /** Releases a ticket added by {@link #force} with the same owner, chunk and {@code ticking}. */
        void release(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean ticking);
    }
}
