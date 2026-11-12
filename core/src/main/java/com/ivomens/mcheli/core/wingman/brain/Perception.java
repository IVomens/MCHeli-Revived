package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.airfield.Airfield;

import javax.annotation.Nullable;

/**
 * Everything a wingman brain decides from, apart from the aircraft's own motion and controls (which
 * it works through {@link Controls}).
 *
 * @param tag      short id of the aircraft for log lines
 * @param leader   the formation leader, or null for an autonomous aircraft
 * @param senses   the world around the aircraft, asked as the brain needs it
 * @param airfield the level's markers and taxi routes
 * @param log      where the brain reports its decisions and setup problems
 */
public record Perception(String tag, Airframe airframe, @Nullable LeaderState leader, Senses senses,
                         Airfield airfield, Tuning tuning, BrainLog log) {
}
