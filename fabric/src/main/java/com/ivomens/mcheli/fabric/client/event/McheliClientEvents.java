package com.ivomens.mcheli.fabric.client.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client events Fabric API lacks that more than MCHeli's own hooks listen to (the client scenes of the
 * game tests time frames and log entity renders). The client mixins fire them where NeoForge fires its
 * counterparts; points only MCHeli's hooks need call those hooks directly.
 */
public final class McheliClientEvents {

    /** Before the game renderer draws a frame: NeoForge's {@code RenderFrameEvent.Pre}. */
    public static final Event<Frame> FRAME_START = EventFactory.createArrayBacked(Frame.class, listeners -> delta -> {
        for (Frame listener : listeners) {
            listener.onFrame(delta);
        }
    });

    /** After the game renderer drew a frame: NeoForge's {@code RenderFrameEvent.Post}. */
    public static final Event<Frame> FRAME_END = EventFactory.createArrayBacked(Frame.class, listeners -> delta -> {
        for (Frame listener : listeners) {
            listener.onFrame(delta);
        }
    });

    /** A living entity is about to be drawn and MCHeli did not cancel it: NeoForge's {@code RenderLivingEvent.Pre}. */
    public static final Event<RenderLiving> RENDER_LIVING_START = EventFactory.createArrayBacked(RenderLiving.class,
            listeners -> entity -> {
                for (RenderLiving listener : listeners) {
                    listener.onRenderLiving(entity);
                }
            });

    private McheliClientEvents() {
    }

    @FunctionalInterface
    public interface Frame {
        void onFrame(DeltaTracker delta);
    }

    @FunctionalInterface
    public interface RenderLiving {
        void onRenderLiving(LivingEntity entity);
    }
}
