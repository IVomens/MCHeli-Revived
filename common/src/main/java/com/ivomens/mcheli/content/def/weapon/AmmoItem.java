package com.ivomens.mcheli.content.def.weapon;

import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ReadContext;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.ValueShape;
import com.ivomens.mcheli.core.content.schema.YamlEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An item that a reload uses up, {@code count} at a time:
 * <pre>
 * AmmoItems:
 * - Count: 2       # or Num; 1 to 64, default 1
 *   Name: iron_ingot   # or Loc
 *   Meta: 0        # or Damage; 0 to 32767, default 1
 * </pre>
 *
 * @param item the item id; a name that is not a valid id becomes {@code minecraft:air}
 */
public record AmmoItem(int count, ResourceLocation item, int meta) {

    /** A legacy TXT weapon has at most this many {@code Item} lines; further ones are ignored. */
    public static final int MAX_TXT_ITEMS = 3;

    public static final ValueCodec<List<AmmoItem>> LIST_CODEC = new ValueCodec<>() {
        @Override
        public List<AmmoItem> fromYaml(Object raw, List<AmmoItem> previous, ReadContext context) {
            List<?> entries = Codecs.list(raw);
            List<AmmoItem> items = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                items.add(readEntry(YamlEntry.of(entries.get(i), context, "ammo item " + (i + 1))));
            }
            return items;
        }

        @Override
        public List<AmmoItem> fromText(String text, List<AmmoItem> previous, ReadContext context) {
            throw new UnsupportedOperationException("TXT ammo items are read by a TXT rule");
        }

        @Override
        public ValueShape shape() {
            return ValueShape.listOf(ValueShape.keys()
                    .key("Count", ValueShape.whole(1, 64)).key("Num", ValueShape.whole(1, 64))
                    .key("Name", ValueShape.text()).key("Loc", ValueShape.text())
                    .key("Meta", ValueShape.whole(0, Short.MAX_VALUE)).key("Damage", ValueShape.whole(0, Short.MAX_VALUE))
                    .build()).note("Items a reload uses up: Name (or Loc) is the item id, Count (or Num) how many.");
        }

        @Override
        public Object toYaml(List<AmmoItem> items) {
            return items.stream().map(AmmoItem::toYaml).toList();
        }
    };

    public static AmmoItem of(int count, String name, int meta) {
        ResourceLocation item = ResourceLocation.tryParse(name.trim().toLowerCase(Locale.ROOT));
        return new AmmoItem(count, item != null ? item : ResourceLocation.withDefaultNamespace("air"), meta);
    }

    private static AmmoItem readEntry(YamlEntry entry) {
        int count = entry.read(Codecs.ints(1, 64), 1, "Count", "Num");
        String name = entry.require(Codecs.LOWER_TEXT, "Loc", "Name");
        int meta = entry.read(Codecs.ints(0, Short.MAX_VALUE), 1, "Meta", "Damage");
        entry.reportUnusedKeys();
        return of(count, name, meta);
    }

    private Map<String, Object> toYaml() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Count", this.count);
        map.put("Name", this.item.toString());
        map.put("Meta", this.meta);
        return map;
    }
}
