package com.ivomens.mcheli.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * What common code may ask of the client this game runs on: the player at the keyboard, the camera
 * and sounds only that player hears. A dedicated server has no such client and gives the answers of
 * the defaults here.
 */
public interface LocalClient {

    LocalClient INSTANCE = Services.sided(LocalClient.class, new LocalClient() {
    });

    static LocalClient get() {
        return INSTANCE;
    }

    /** The player at the keyboard, or null on a server or before a world is joined. */
    @Nullable
    default Player player() {
        return null;
    }

    /**
     * Whether the entity is the player at the keyboard. It must belong to a client level, and is
     * compared by entity id, the way the client level identifies it.
     */
    default boolean isLocalPlayer(@Nullable Entity entity) {
        Player player = player();
        return entity instanceof Player && entity.level().isClientSide && player != null
                && player.getId() == entity.getId();
    }

    /** Whether the camera is in first person. */
    default boolean isFirstPerson() {
        return false;
    }

    /** Whether the game runs a single player world, here or on the running integrated server. */
    default boolean isSingleplayer() {
        MinecraftServer server = Services.PLATFORM.getServer();
        return server != null && server.isSingleplayer();
    }

    /** Plays a sound at the player's ear, the way the HUD beeps and warns. */
    default void playUiSound(ResourceLocation sound, float volume, float pitch) {
    }

    /** Plays the button click at the player's ear. */
    default void playClick() {
    }

    /**
     * Sets the post effect (night or thermal vision) a camera view applies from the next time it takes
     * over the view, or removes ours with null.
     */
    default void useVisionEffect(@Nullable ResourceLocation postEffect) {
    }

    /**
     * Kicks the player's view by a weapon's recoil: up by {@code pitch} and sideways by {@code yaw} degrees,
     * decaying by {@code control} every tick.
     */
    default void kickView(float pitch, float yaw, float control) {
    }

    /** Lets the player start breaking the next block without the usual delay. */
    default void skipBlockBreakDelay() {
    }

    /** Starts a sound at a position, heard on this client only; null when nothing plays. */
    @Nullable
    default PlayingSound playSound(ResourceLocation sound, float volume, float pitch, Vec3 at) {
        return null;
    }

    /** A sound started by {@link #playSound}. */
    interface PlayingSound {

        void stop();
    }
}
