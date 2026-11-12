package com.ivomens.mcheli.inspector;

import com.ivomens.mcheli.golden.MinecraftBootstrap;
import com.ivomens.mcheli.inspector.net.InspectorReplyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The inspector's snapshot record: its codec round trip, its size bound and the comparison of two sides. */
class InspectorSnapshotCodecTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.ensure();
    }

    @Test
    void snapshotSurvivesARoundTrip() {
        SnapshotBuilder builder = new SnapshotBuilder(4711, "helicopter/ah-64", 123_456_789L);
        SnapshotBuilder.SectionWriter state = builder.section(InspectorTab.OVERVIEW, "State", "");
        state.put("health", "80 / 100");
        state.put("throttle", 0.45);
        state.put("destroyed", false);
        builder.section(InspectorTab.COMPONENTS, "EngineComponent", "SYSTEMS  step 3: engine")
                .entry("crippled", "no", "", false);
        builder.section(InspectorTab.DEFINITION, "Physics", "")
                .entry("Speed", "1.25", "line 12, default 0.5", true);
        builder.section(InspectorTab.CREW, "Seat 0", "pilot").put("rider", "Dév ✈");
        InspectorSnapshot snapshot = builder.build();

        InspectorSnapshot copy = roundTrip(snapshot);

        assertEquals(snapshot, copy);
        assertFalse(copy.truncated());
        assertEquals("0.450", copy.value(InspectorTab.OVERVIEW, "State", "throttle").orElseThrow());
        assertEquals(List.of("Speed"), copy.sections(InspectorTab.DEFINITION).getFirst().entries().stream()
                .map(InspectorEntry::key).toList());
    }

    @Test
    void replyMessageSurvivesARoundTrip() {
        InspectorReplyMessage denied = new InspectorReplyMessage(InspectorReplyMessage.Status.DENIED,
                InspectorSnapshot.empty(7));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        InspectorReplyMessage.TYPE.codec().encode(buffer, denied);
        InspectorReplyMessage copy = InspectorReplyMessage.TYPE.codec().decode(buffer);
        assertEquals(denied, copy);
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void builderKeepsTheEncodedSizeWithinTheBound() {
        SnapshotBuilder builder = new SnapshotBuilder(1, "plane/" + "x".repeat(400), Long.MAX_VALUE);
        String longText = "€".repeat(1000);
        for (int section = 0; section < 400; section++) {
            SnapshotBuilder.SectionWriter out = builder.section(InspectorTab.COMPONENTS, "Section " + section + longText,
                    longText);
            for (int entry = 0; entry < 300; entry++) {
                out.entry("key " + entry + longText, longText, longText, entry % 2 == 0);
            }
        }
        InspectorSnapshot snapshot = builder.build();

        int encoded = encodedSize(snapshot);

        assertTrue(snapshot.truncated(), "a snapshot past the budget is marked truncated");
        assertTrue(encoded <= builder.size(), "the builder's count is an upper bound: " + encoded + " > " + builder.size());
        assertTrue(encoded <= InspectorSnapshot.MAX_BYTES, encoded + " bytes is over the bound");
        assertTrue(encoded > InspectorSnapshot.MAX_BYTES * 9 / 10, "the budget is used, not wasted: " + encoded);
        assertEquals(snapshot, roundTrip(snapshot));
    }

    @Test
    void builderCutsTextsToTheirCodecLengths() {
        SnapshotBuilder builder = new SnapshotBuilder(1, "tank/m1a2", 0L);
        builder.section(InspectorTab.WEAPONS, "w".repeat(200), "").put("k".repeat(100), "v".repeat(500));
        InspectorSection section = builder.build().sections().getFirst();
        InspectorEntry entry = section.entries().getFirst();

        assertEquals(InspectorSection.MAX_TITLE, section.title().length());
        assertEquals(InspectorEntry.MAX_KEY, entry.key().length());
        assertEquals(InspectorEntry.MAX_VALUE, entry.value().length());
        assertTrue(entry.value().endsWith("..."));
    }

    @Test
    void codecRefusesMoreThanItsBounds() {
        List<InspectorEntry> entries = new ArrayList<>();
        for (int i = 0; i <= InspectorSection.MAX_ENTRIES; i++) {
            entries.add(new InspectorEntry("k" + i, "v", "", false));
        }
        InspectorSnapshot tooManyEntries = new InspectorSnapshot(1, "x", 0L, false,
                List.of(new InspectorSection(InspectorTab.CREW, "Seat 0", "", entries)));
        InspectorSnapshot tooLong = new InspectorSnapshot(1, "x", 0L, false, List.of(new InspectorSection(
                InspectorTab.CREW, "Seat 0", "", List.of(new InspectorEntry("k", "v".repeat(129), "", false)))));

        assertThrows(RuntimeException.class, () -> encodedSize(tooManyEntries));
        assertThrows(RuntimeException.class, () -> encodedSize(tooLong));
    }

    @Test
    void sidesAgreeWithinTheTolerance() {
        assertTrue(SnapshotDiff.agree("12.000, 64.000, -3.000", "12.020, 64.010, -3.000"));
        assertFalse(SnapshotDiff.agree("80 / 100", "60 / 100"));
        assertFalse(SnapshotDiff.agree("yes", "no"));
        assertEquals(SnapshotDiff.Verdict.DRIFT, SnapshotDiff.compare("position", "1, 2, 3", "9, 2, 3"));
        assertEquals(SnapshotDiff.Verdict.MISMATCH, SnapshotDiff.compare("gunner mode", "yes", "no"));
        assertEquals(SnapshotDiff.Verdict.ONE_SIDED, SnapshotDiff.compare("gunner mode", "yes", null));
    }

    private static InspectorSnapshot roundTrip(InspectorSnapshot snapshot) {
        ByteBuf buffer = Unpooled.buffer();
        InspectorSnapshot.CODEC.encode(buffer, snapshot);
        InspectorSnapshot copy = InspectorSnapshot.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "bytes left over");
        return copy;
    }

    private static int encodedSize(InspectorSnapshot snapshot) {
        ByteBuf buffer = Unpooled.buffer();
        InspectorSnapshot.CODEC.encode(buffer, snapshot);
        return buffer.readableBytes();
    }
}
