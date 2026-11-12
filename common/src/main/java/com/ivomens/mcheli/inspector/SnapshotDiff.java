package com.ivomens.mcheli.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the client's snapshot of a vehicle with the server's, key by key. The server's is a few ticks old
 * by the time it arrives, so numbers agree within a small tolerance, and values that change every tick (the
 * position, the age, counters) are told apart from real mismatches.
 */
public final class SnapshotDiff {

    /** How the two sides compare on one entry. */
    public enum Verdict {
        /** Only one side has the entry. */
        ONE_SIDED,
        /** The values agree. */
        SAME,
        /**
         * The values differ as expected: one that changes every tick while the server's view is in flight, or
         * one each side keeps for itself.
         */
        DRIFT,
        /** The values differ: a possible desync. */
        MISMATCH
    }

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE]-?\\d+)?");
    private static final double ABSOLUTE = 0.05;
    private static final double RELATIVE = 0.02;
    /** Keys whose value moves every tick, so a difference is expected while the server's view travels. */
    private static final Set<String> MOVING = Set.of("age", "position", "velocity (m/tick)", "speed (km/h)", "yaw",
            "pitch", "roll", "previous", "synced", "angle", "sweep angle", "target", "still ticks",
            "blend ticks left", "invulnerable ticks", "repair cooldown", "flare cooldown", "previous throttle",
            "motion y before physics", "rest", "status", "fuel", "cooldown", "heat", "reload ticks", "lock");
    /** Keys whose value is one side's own bookkeeping, never synced, so the sides differ by design. */
    private static final Set<String> LOCAL = Set.of("unique id", "counts requested", "seats created", "seat entity",
            "reload linger ticks", "controls queued", "smoothing", "synced station id", "station", "paired station",
            "last attacker", "on ground before physics", "fold cooldown", "mode switch cooldown");

    private SnapshotDiff() {
    }

    /** How {@code client} and {@code server} compare under {@code key}; null values mean one side lacks it. */
    public static Verdict compare(String key, String client, String server) {
        if (client == null || server == null) {
            return Verdict.ONE_SIDED;
        }
        if (agree(client, server)) {
            return Verdict.SAME;
        }
        return MOVING.contains(key) || LOCAL.contains(key) ? Verdict.DRIFT : Verdict.MISMATCH;
    }

    /** Equal text, or the same text around numbers that each agree within the tolerance. */
    public static boolean agree(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        List<Double> numbersA = new ArrayList<>();
        List<Double> numbersB = new ArrayList<>();
        if (!skeleton(a, numbersA).equals(skeleton(b, numbersB)) || numbersA.size() != numbersB.size()
                || numbersA.isEmpty()) {
            return false;
        }
        for (int i = 0; i < numbersA.size(); i++) {
            double x = numbersA.get(i);
            double y = numbersB.get(i);
            if (Math.abs(x - y) > Math.max(ABSOLUTE, RELATIVE * Math.max(Math.abs(x), Math.abs(y)))) {
                return false;
            }
        }
        return true;
    }

    /** The server's value of the entry the client shows, if the server sent one. */
    public static Optional<String> serverValue(InspectorSnapshot server, InspectorSection clientSection,
                                               InspectorEntry clientEntry) {
        return server.value(clientSection.tab(), clientSection.title(), clientEntry.key());
    }

    /** How many entries both sides have whose values are a real mismatch. */
    public static int mismatches(InspectorSnapshot client, InspectorSnapshot server) {
        int count = 0;
        for (InspectorSection section : client.sections()) {
            for (InspectorEntry entry : section.entries()) {
                String other = serverValue(server, section, entry).orElse(null);
                if (compare(entry.key(), entry.value(), other) == Verdict.MISMATCH) {
                    count++;
                }
            }
        }
        return count;
    }

    /** The text with every number replaced by '#', collecting the numbers. */
    private static String skeleton(String text, List<Double> numbers) {
        Matcher matcher = NUMBER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
            matcher.appendReplacement(out, "#");
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
