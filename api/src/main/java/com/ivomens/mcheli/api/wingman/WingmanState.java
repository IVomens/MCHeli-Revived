package com.ivomens.mcheli.api.wingman;

import com.ivomens.mcheli.api.vehicle.Vehicle;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * One wingman as it was when {@link Wingmen} was asked: a snapshot that does not change afterwards. Ask again
 * for fresh state.
 *
 * <p>Server thread. Since API 1.1; implemented by MCHeli only, so a minor version may add methods.
 */
public interface WingmanState {

    /** The UUID of the wingman's aircraft, which every order takes. */
    UUID aircraftId();

    /** The aircraft, or null when it is not loaded right now (the wingman keeps its place on the roster). */
    @Nullable
    Vehicle aircraft();

    /** The player who enlisted it; null when only operators may order it. */
    @Nullable
    UUID owner();

    /** The aircraft (or other entity) it flies formation on; null when it follows no one. */
    @Nullable
    Entity leader();

    /** Its place in the leader's formation, from 0; 0 when it follows no one. */
    int formationSlot();

    /** Whether and how it attacks. */
    WingmanAttack attack();

    /** The target it was told to attack, with {@link WingmanAttack#TARGET}; null for its leader's lock. */
    @Nullable
    UUID attackTarget();

    /** The weapon type it prefers, such as {@code missile}; null when it may use any. */
    @Nullable
    String weaponType();

    /**
     * What it is doing, as the wingman panel and {@code /wingman status} show it: {@code FOLLOWING} or
     * {@code IDLE}, or while flying a mission the phase of the flight, such as {@code TRANSIT_TO},
     * {@code ON_STATION}, {@code RTB}, {@code LANDING} or {@code PARKED}. More phases may come.
     */
    String status();

    /** The kinds of the mission order it flies; empty when it has none. */
    Set<MissionKind> missionKinds();
}
