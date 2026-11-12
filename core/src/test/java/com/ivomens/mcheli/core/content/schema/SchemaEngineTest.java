package com.ivomens.mcheli.core.content.schema;

import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema engine without a game: codecs read, clamp and write values, schemas map TXT keys to their fields
 * and rules, and a file that sets nothing builds the declared defaults. The readers that drive the engine over
 * real files are tested with the content goldens in {@code :neoforge}.
 */
class SchemaEngineTest {

    private final List<String> warnings = new ArrayList<>();
    private final ReadContext context = this.warnings::add;

    @Test
    void rangedIntegersAreClampedWithAWarning() {
        ValueCodec<Integer> codec = Codecs.ints(1, 64);
        assertEquals(16, codec.fromYaml(16, 1, this.context));
        assertEquals(64, codec.fromYaml(500, 1, this.context));
        assertEquals(1, codec.fromText("0", 1, this.context));
        assertEquals(List.of("500 is outside 1 to 64, 64 is used", "0 is outside 1 to 64, 1 is used"), this.warnings);
    }

    @Test
    void textNumbersAreParsedTheLegacyWay() {
        assertEquals(12, Codecs.parseInt(" 12 "));
        assertEquals(0.5F, Codecs.parseFloat("0.5"));
        assertEquals(2.25, Codecs.parseDouble("2.25"));
        assertThrows(ContentValueException.class, () -> Codecs.parseInt("high"));
    }

    @Test
    void wrongYamlTypesAreReportedAsContentErrors() {
        ContentValueException error = assertThrows(ContentValueException.class,
                () -> Codecs.ints(0, 10).fromYaml("ten", 0, this.context));
        assertTrue(error.getMessage().startsWith("expected"), error.getMessage());
    }

    @Test
    void coloursReadEveryHexSpelling() {
        assertEquals(0xFFAA00, ArgbColor.parseHex("#FFAA00"));
        assertEquals(0xFFAA00, ArgbColor.parseHex("0xFFAA00"));
        assertEquals(0xFFAA00, ArgbColor.parseHex("FFAA00"));
        assertEquals(0x80FFAA00, ArgbColor.parseHex("80FFAA00"));
        assertEquals(0x80FFAA00, ArgbColor.ofArgb(0x80FFAA00).argb());
        assertThrows(ContentValueException.class, () -> ArgbColor.parseHex(""));
    }

    @Test
    void localizedNamesKeepTheirTranslations() {
        LocalizedName name = LocalizedName.of("M18").with("ja_JP", "M18 発煙弾");
        assertEquals("M18", name.name());
        assertEquals("M18 発煙弾", name.translations().get("ja_JP"));
        assertEquals("M26", name.withName("M26").name());
    }

    @Test
    void aFileThatSetsNothingBuildsTheDeclaredDefaults() {
        ThrowableDefinition grenade = ThrowableSchema.SCHEMA.defaults("m18");
        assertEquals("m18", grenade.name());
        assertEquals(1, grenade.stackSize());
        assertEquals(10, grenade.aliveTime());
        assertEquals(-0.04F, grenade.gravityInWater());
        assertEquals(ArgbColor.WHITE, grenade.smoke().color());
    }

    @Test
    void txtKeysNameTheirFieldOrRule() {
        Schema<ThrowableDefinition> schema = ThrowableSchema.SCHEMA;
        assertInstanceOf(TxtKey.SetsField.class, schema.txtKey("stacksize").orElseThrow());
        assertInstanceOf(TxtKey.AppliesRule.class, schema.txtKey("addrecipe").orElseThrow());
        assertInstanceOf(TxtKey.Unsupported.class, schema.txtKey("particle").orElseThrow());
        assertTrue(schema.txtKey("relaodtime").isEmpty());
    }

    @Test
    void txtValuesAreConvertedAfterTheyAreRead() {
        Field<?, ?> timeFuse = ((TxtKey.SetsField) ThrowableSchema.SCHEMA.txtKey("timefuse").orElseThrow()).field();
        assertEquals("TimeFuse", timeFuse.key());
        assertEquals(60, afterTxt(timeFuse, 3), "TXT gives seconds, the definition keeps ticks");
    }

    @Test
    void valuesRememberWhatTheFileSet() {
        Field<ThrowableDefinition, Integer> power = Field.of("Power", Codecs.INT, 0, ThrowableDefinition::power);
        Values values = new Values("test");
        assertEquals(0, values.get(power));
        values.set(power, 7);
        assertTrue(values.isSet(power));
        assertEquals(7, values.get(power));
    }

    @SuppressWarnings("unchecked")
    private static <T> T afterTxt(Field<?, ?> field, T value) {
        return ((Field<?, T>) field).afterTxt(value);
    }
}
