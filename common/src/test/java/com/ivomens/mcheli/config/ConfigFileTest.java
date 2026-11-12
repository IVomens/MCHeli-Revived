package com.ivomens.mcheli.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileTest {

    private static final IntOption COUNT = IntOption.of("Count", 5).range(1, 10);
    private static final IntOption STRICT = IntOption.of("Strict", 5).range(1, 10, RangePolicy.USE_DEFAULT);
    private static final DoubleOption SCALE = DoubleOption.of("Scale", 1.0).range(0.5, 2.0).describe("the scale");
    private static final BoolOption ENABLED = BoolOption.of("Enabled", true);
    private static final StringOption NEW_NAME = StringOption.of("NewName", "a").since("1.0.0");
    private static final ListOption NAMES = new ListOption(List.of("Name", "Alias"),
            List.of(new ListOption.Entry("Name", "default")));
    private static final BoolOption FLAG = BoolOption.of("Verbose", false);

    private static final ConfigFile FILE = new ConfigFile(List.of(
            Layout.text("[test]"),
            Layout.entry(COUNT), Layout.entry(STRICT), Layout.entry(SCALE), Layout.entry(ENABLED),
            Layout.entry(NEW_NAME),
            new Layout.When(values -> values.get(FLAG), List.of(Layout.text("Verbose"))),
            Layout.blank(),
            Layout.entry(NAMES)), List.of(FLAG), true, false);

    private final List<String> problems = new ArrayList<>();

    private ConfigValues read(String... lines) {
        return FILE.read(List.of(lines), (line, problem) -> this.problems.add(line + ": " + problem));
    }

    @Test
    void aWordForANumberFallsBackToTheDefault() {
        ConfigValues values = read("Count = 7", "Count = lots", "Scale = many");
        assertEquals(5, values.get(COUNT));
        assertEquals(1.0, values.get(SCALE));
        assertEquals(List.of("2: Count = lots is not a whole number; using the default, 5",
                "3: Scale = many is not a number; using the default, 1.00"), this.problems);
    }

    @Test
    void outOfRangeValuesAreClampedOrReset() {
        ConfigValues values = read("count = 50", "STRICT = 50", "Scale = 0,1");
        assertEquals(10, values.get(COUNT));
        assertEquals(5, values.get(STRICT));
        assertEquals(0.5, values.get(SCALE));
        assertEquals(3, this.problems.size());
    }

    @Test
    void aBooleanKeepsItsValueWhenTheWordIsNeitherTrueNorFalse() {
        assertFalse(read("Enabled = FALSE", "Enabled = maybe").get(ENABLED));
        assertEquals(List.of("2: Enabled = maybe is not true or false; keeping false"), this.problems);
    }

    @Test
    void optionsNewerThanTheFileAreIgnored() {
        assertEquals("a", read("MOD_Version = 0.9.0", "NewName = b").get(NEW_NAME));
        assertEquals("b", read("MOD_Version = 1.0.0", "NewName = b").get(NEW_NAME));
        assertEquals("a", read("NewName = b").get(NEW_NAME));
        assertEquals("b", read("MOD_Version = 1.x.0", "NewName = b").get(NEW_NAME));
    }

    @Test
    void listsCollectEveryLineInOrderAndUseTheDefaultOnlyWhenEmpty() {
        assertEquals(NAMES.defaultValue(), read("Count = 3").get(NAMES));
        assertEquals(List.of(new ListOption.Entry("Name", "x"), new ListOption.Entry("Alias", "y"),
                new ListOption.Entry("Name", "z")), read("name = x", "ALIAS = y", "Name = z").get(NAMES));
    }

    @Test
    void linesWithoutExactlyOneEqualsSignAreSkipped() {
        ConfigValues values = read("Count = 2 = 3", "Count", "Count =");
        assertFalse(values.isSet(COUNT));
        assertTrue(this.problems.isEmpty());
    }

    @Test
    void theFileIsWrittenFromTheLayout() {
        ConfigValues values = read("Verbose", "Alias = y", "Unknown = 1", "Scale = 1.234");
        assertEquals(List.of("[test]", "Count = 5", "Strict = 5", ";the scale", "Scale = 1.23", "Enabled = true",
                "NewName = a", "Verbose", "", "Alias = y"), FILE.render(values));
    }
}
