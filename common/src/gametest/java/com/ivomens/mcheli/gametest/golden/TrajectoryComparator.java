package com.ivomens.mcheli.gametest.golden;

import java.util.Optional;

/**
 * Compares two trajectories cell by cell, exactly. The first difference is reported with its tick,
 * column and both values, which is usually enough to find the step that changed.
 */
final class TrajectoryComparator {

    private TrajectoryComparator() {
    }

    /** A description of the first difference, or empty when the data is identical. */
    static Optional<String> firstDifference(GoldenFile expected, GoldenFile actual) {
        if (!expected.columns().equals(actual.columns())) {
            return Optional.of("columns changed: expected [" + expected.columns() + "] but was ["
                    + actual.columns() + "]");
        }
        String[] names = expected.columns().split(",", -1);
        int rows = Math.min(expected.rows().size(), actual.rows().size());
        for (int i = 0; i < rows; i++) {
            String[] want = expected.rows().get(i).split(",", -1);
            String[] got = actual.rows().get(i).split(",", -1);
            for (int c = 0; c < names.length; c++) {
                String wantCell = c < want.length ? want[c] : "<missing>";
                String gotCell = c < got.length ? got[c] : "<missing>";
                if (!wantCell.equals(gotCell)) {
                    return Optional.of("tick " + i + ", column " + names[c] + ": expected " + wantCell
                            + " but was " + gotCell);
                }
            }
        }
        if (expected.rows().size() != actual.rows().size()) {
            return Optional.of("expected " + expected.rows().size() + " ticks but recorded " + actual.rows().size());
        }
        return Optional.empty();
    }
}
