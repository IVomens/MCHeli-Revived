package com.ivomens.mcheli.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The hysteresis behind the takeoff, landing and water events: bounces and short dips do not count. */
class VehicleContactWatchTest {

    private static final int ON = VehicleContactWatch.Debounced.BECAME_ON;
    private static final int OFF = VehicleContactWatch.Debounced.BECAME_OFF;

    /** Feeds one answer per tick and lists the ticks a change settled, as "on@tick" or "off@tick". */
    private static List<String> run(VehicleContactWatch.Debounced state, boolean[] answers, int onTicks, int offTicks) {
        List<String> changes = new ArrayList<>();
        for (int tick = 0; tick < answers.length; tick++) {
            int change = state.update(answers[tick], onTicks, offTicks);
            if (change == ON) {
                changes.add("on@" + tick);
            } else if (change == OFF) {
                changes.add("off@" + tick);
            }
        }
        return changes;
    }

    private static boolean[] ticks(Object... runs) {
        List<Boolean> all = new ArrayList<>();
        for (int i = 0; i < runs.length; i += 2) {
            for (int n = 0; n < (Integer) runs[i + 1]; n++) {
                all.add((Boolean) runs[i]);
            }
        }
        boolean[] answers = new boolean[all.size()];
        for (int i = 0; i < answers.length; i++) {
            answers[i] = all.get(i);
        }
        return answers;
    }

    @Test
    void theFirstSettledStateRaisesNothing() {
        assertEquals(List.of(), run(new VehicleContactWatch.Debounced(), ticks(true, 30), 10, 20));
        assertEquals(List.of(), run(new VehicleContactWatch.Debounced(), ticks(false, 30), 10, 20));
    }

    @Test
    void aChangeSettlesAfterItsTicks() {
        // down for 15 ticks (settles), up for 25 (takes off on the 20th), down for 10 (lands on the 10th)
        assertEquals(List.of("off@34", "on@49"),
                run(new VehicleContactWatch.Debounced(), ticks(true, 15, false, 25, true, 10), 10, 20));
    }

    @Test
    void bouncesShorterThanTheTicksDoNotCount() {
        // settled down, then hops of 19 ticks and touchdowns of 9 never settle
        boolean[] answers = ticks(true, 12, false, 19, true, 3, false, 19, true, 12);
        assertEquals(List.of(), run(new VehicleContactWatch.Debounced(), answers, 10, 20));
        // in the air, touching down for 9 ticks at a time is no landing
        answers = ticks(false, 25, true, 9, false, 5, true, 9, false, 5);
        assertEquals(List.of(), run(new VehicleContactWatch.Debounced(), answers, 10, 20));
    }

    @Test
    void forgettingStartsOverWithoutAnEvent() {
        VehicleContactWatch.Debounced state = new VehicleContactWatch.Debounced();
        run(state, ticks(true, 15), 10, 20);
        state.forget();
        assertEquals(List.of(), run(state, ticks(false, 30), 10, 20));
        assertEquals(List.of("on@9"), run(state, ticks(true, 10), 10, 20));
    }
}
