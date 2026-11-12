package com.ivomens.mcheli.content.def.vehicle;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.InlineList;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.YamlEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML {@code ExcludeWith} of a seat or rack: the seats and racks, numbered from 1, that cannot be taken while
 * this one is.
 * <pre>
 * ExcludeWith:
 *   Seats: [3]
 *   Racks: [5]
 * </pre>
 */
public record SeatExclusions(List<Integer> seats, List<Integer> racks) {

    public static final SeatExclusions NONE = new SeatExclusions(List.of(), List.of());

    public static final ValueCodec<SeatExclusions> CODEC = new ValueCodec<>() {
        @Override
        public SeatExclusions fromYaml(Object raw, SeatExclusions previous, ReadContext context) {
            YamlEntry entry = YamlEntry.of(raw, context, "ExcludeWith");
            SeatExclusions read = new SeatExclusions(numbers(entry.raw("Seats")), numbers(entry.raw("Racks")));
            entry.reportUnusedKeys();
            return read;
        }

        @Override
        public SeatExclusions fromText(String text, SeatExclusions previous, ReadContext context) {
            throw new UnsupportedOperationException("TXT files group exclusive seats with ExclusionSeat lines");
        }

        @Override
        public ValueShape shape() {
            return ValueShape.keys().key("Seats", ValueShape.listOf(ValueShape.anything()))
                    .key("Racks", ValueShape.listOf(ValueShape.anything())).build()
                    .note("Seats and racks, by number, that cannot be used while this seat is taken.");
        }

        @Override
        public Object toYaml(SeatExclusions value) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (!value.seats.isEmpty()) {
                map.put("Seats", new InlineList(value.seats));
            }
            if (!value.racks.isEmpty()) {
                map.put("Racks", new InlineList(value.racks));
            }
            return map;
        }
    };

    public SeatExclusions {
        seats = List.copyOf(seats);
        racks = List.copyOf(racks);
    }

    SeatExclusions withSeat(int seat) {
        List<Integer> more = new ArrayList<>(this.seats);
        more.add(seat);
        return new SeatExclusions(more, this.racks);
    }

    SeatExclusions withRack(int rack) {
        List<Integer> more = new ArrayList<>(this.racks);
        more.add(rack);
        return new SeatExclusions(this.seats, more);
    }

    /** Numbers are kept as written; the game skips those below 1, which name no seat. */
    private static List<Integer> numbers(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<Integer> numbers = new ArrayList<>();
        for (Object element : Codecs.list(raw)) {
            if (element != null) {
                numbers.add(Codecs.number(element).intValue());
            }
        }
        return numbers;
    }
}
