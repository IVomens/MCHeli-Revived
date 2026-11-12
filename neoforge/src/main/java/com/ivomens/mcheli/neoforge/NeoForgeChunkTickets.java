package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.ChunkTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.ArrayList;
import java.util.UUID;

/**
 * One {@link TicketController} per ticket set. The controllers are registered in
 * {@code RegisterTicketControllersEvent} by {@link McheliNeoForge}.
 */
public class NeoForgeChunkTickets implements ChunkTickets {

    @Override
    public TicketSet create(ResourceLocation id) {
        return new Controlled(new TicketController(id, NeoForgeChunkTickets::discardSavedTickets));
    }

    /** The controller behind a ticket set made by this class. */
    static TicketController controller(TicketSet tickets) {
        return ((Controlled) tickets).controller;
    }

    /**
     * Ticket validation callback: our users rebuild their tickets at runtime, so tickets saved by a
     * previous session are never reinstated.
     */
    private static void discardSavedTickets(ServerLevel level, TicketHelper helper) {
        for (UUID owner : new ArrayList<>(helper.getEntityTickets().keySet())) {
            helper.removeAllTickets(owner);
        }
        for (BlockPos owner : new ArrayList<>(helper.getBlockTickets().keySet())) {
            helper.removeAllTickets(owner);
        }
    }

    private record Controlled(TicketController controller) implements TicketSet {

        @Override
        public void force(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean ticking) {
            controller.forceChunk(level, owner, chunkX, chunkZ, true, ticking);
        }

        @Override
        public void release(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean ticking) {
            controller.forceChunk(level, owner, chunkX, chunkZ, false, ticking);
        }
    }
}
