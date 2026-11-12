package com.ivomens.mcheli.api.wingman;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MCHeli's AI wingmen: reading who has which and what they do, and giving them the orders the wingman panel
 * and the {@code /wingman} command give, with the same rules and the same result.
 *
 * <pre>{@code
 * Wingmen wingmen = McheliApi.get().wingmen();
 * if (wingmen.follow(player, nearbyJet) == WingmanOrderResult.DONE) {
 *     wingmen.attack(player, nearbyJet.getUUID(), enemy);
 * }
 * for (WingmanState wingman : wingmen.orderableBy(player)) {
 *     LOGGER.info("{}: {}", wingman.aircraftId(), wingman.status());
 * }
 * }</pre>
 *
 * <p><b>Who may order.</b> Every order names the player who gives it, the commander, and is refused
 * ({@link WingmanOrderResult#NOT_ALLOWED}) when that player could not give it in game: a wingman takes orders
 * from its owner, from operators, and from whoever flies the aircraft it follows; an aircraft that is nobody's
 * wingman yet becomes the commander's when an order enlists it, unless another player is aboard or it is locked
 * to its owner. Orders tell the commander nothing in chat; the result says what happened.
 *
 * <p><b>Formations.</b> A wingman flies in a numbered slot behind its leader. The spacing of the slots and the
 * largest formation are server settings ({@code mcheli-wingman.cfg}, {@code /wingman dist} and
 * {@code /wingman maxwings}), the same for every formation, and not set through the API.
 *
 * <p><b>Side and thread.</b> Server side, on the server thread. The roster is the running server's and is not
 * saved: it is empty after a restart.
 *
 * <p><b>Stability.</b> Since API 1.1. Implemented by MCHeli only; a minor version may add methods.
 */
public interface Wingmen {

    /** Every wingman of the server, in the order they joined. */
    List<WingmanState> all(MinecraftServer server);

    /** The wingmen the player may order, as the wingman panel lists them; operators get all of them. */
    List<WingmanState> orderableBy(ServerPlayer player);

    /** The wingmen flying formation on the leader (an aircraft), by slot order of joining; empty for null. */
    List<WingmanState> following(@Nullable Entity leader);

    /** The wingman flying this aircraft, if it is one. */
    Optional<WingmanState> get(MinecraftServer server, UUID aircraftId);

    /**
     * Puts the aircraft in the next free slot of the formation led by the aircraft the commander flies, as the
     * panel's follow button does. A wingman that already follows someone changes leader and keeps its owner.
     *
     * @return {@link WingmanOrderResult#NOT_IN_AIRCRAFT} when the commander rides no aircraft,
     *         {@link WingmanOrderResult#FORMATION_FULL}, {@link WingmanOrderResult#INVALID} for something that is no
     *         aircraft or is the commander's own, {@link WingmanOrderResult#NOT_ALLOWED}, or
     *         {@link WingmanOrderResult#DONE}
     */
    WingmanOrderResult follow(ServerPlayer commander, Entity aircraft);

    /** Releases the wingman: it leaves its formation, drops its orders and is nobody's wingman any more. */
    WingmanOrderResult release(ServerPlayer commander, UUID aircraftId);

    /** Tells the wingman to attack the target, until told otherwise or the target is gone. */
    WingmanOrderResult attack(ServerPlayer commander, UUID aircraftId, Entity target);

    /** Tells the wingman to pick and attack hostiles on its own. */
    WingmanOrderResult autoAttack(ServerPlayer commander, UUID aircraftId);

    /** Tells the wingman to hold fire, forgetting the target it was given. */
    WingmanOrderResult holdFire(ServerPlayer commander, UUID aircraftId);

    /**
     * Sets the weapon type the wingman prefers, such as {@code missile}, {@code gun} or {@code aamissile}.
     *
     * @param weaponType the type, or null (or empty) to let it use any weapon
     */
    WingmanOrderResult setWeaponType(ServerPlayer commander, UUID aircraftId, @Nullable String weaponType);

    /**
     * Sends the aircraft on a mission order, as the mission planner does; it becomes the commander's wingman if
     * it is nobody's yet. It is fuelled up and starts in the air; a mission it was flying is replaced.
     *
     * @return {@link WingmanOrderResult#NOT_FOUND} when the aircraft is not loaded in the commander's level,
     *         {@link WingmanOrderResult#NOT_ALLOWED}, or {@link WingmanOrderResult#DONE}
     */
    WingmanOrderResult dispatch(ServerPlayer commander, UUID aircraftId, MissionRequest mission);

    /**
     * Calls off the wingman's mission order and the route mission it flies, if any; it stays a wingman. A
     * wingman in formation keeps following its leader.
     */
    WingmanOrderResult abort(ServerPlayer commander, UUID aircraftId);
}
