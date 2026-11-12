package com.ivomens.mcheli.golden.content;

import com.ivomens.mcheli.content.bind.HudYamlReader;
import com.ivomens.mcheli.content.bind.YamlReader;
import com.ivomens.mcheli.core.content.def.hud.HudOp;
import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableDefinition;
import com.ivomens.mcheli.core.content.def.throwable.ThrowableSchema;
import com.ivomens.mcheli.content.def.vehicle.VehicleFile;
import com.ivomens.mcheli.content.def.vehicle.VehicleSchema;
import com.ivomens.mcheli.content.def.weapon.WeaponDefinition;
import com.ivomens.mcheli.content.def.weapon.WeaponSchema;
import com.ivomens.mcheli.content.emit.HudEmitter;
import com.ivomens.mcheli.content.emit.SchemaEmitter;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.core.content.schema.ContentDefinition;
import com.ivomens.mcheli.core.content.schema.Schema;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Content read through a schema survives conversion exactly: definition, emitted YAML, definition again
 * gives an equal record, for the legacy TXT corpus and for every bundled file. This is stricter than the
 * dump comparison of {@link EmitterRoundTripTest}, which only sees the values run-time code reads.
 * HUD programs, which are not one schema, go through the HUD emitter and reader; they compare equal apart
 * from the TXT line numbers YAML has no place for.
 */
class SchemaRoundTripTest {

    private static Schema<?> schemaOf(ContentDefinition definition) {
        return switch (definition) {
            case ThrowableDefinition throwable -> ThrowableSchema.SCHEMA;
            case WeaponDefinition weapon -> WeaponSchema.SCHEMA;
            case VehicleFile vehicle -> VehicleSchema.forKind(vehicle.kind());
            default -> throw new IllegalArgumentException("no schema for " + definition.getClass());
        };
    }

    @TestFactory
    List<DynamicTest> txtCorpus() {
        return roundTrips(TxtFixtures.load());
    }

    @TestFactory
    List<DynamicTest> bundledYaml() {
        return roundTrips(ContentPipeline.load(ContentPipeline.bundledPack()));
    }

    private static List<DynamicTest> roundTrips(Map<String, LoadedContent<?, ?>> parsed) {
        List<DynamicTest> tests = new ArrayList<>();
        parsed.forEach((file, loaded) -> tests.add(DynamicTest.dynamicTest(file,
                () -> checkRoundTrip(file, loaded.entry().definition()))));
        return tests;
    }

    private static void checkRoundTrip(String file, ContentDefinition definition) {
        if (definition instanceof HudProgram program) {
            checkHudRoundTrip(file, program);
            return;
        }
        @SuppressWarnings("unchecked")
        Schema<ContentDefinition> schema = (Schema<ContentDefinition>) schemaOf(definition);
        String yaml = SchemaEmitter.emit(schema, definition);
        ContentDefinition again = YamlReader.read(schema, definition.name(), file, yaml.lines().toList());
        assertEquals(definition, again, () -> "the YAML emitted for " + file + " reads back differently:\n" + yaml);
    }

    private static void checkHudRoundTrip(String file, HudProgram program) {
        String yaml = HudEmitter.emit(program);
        HudProgram again = HudYamlReader.read(program.name(), file, yaml.lines().toList());
        assertEquals(withoutLines(program), withoutLines(again),
                () -> "the YAML emitted for " + file + " reads back differently:\n" + yaml);
    }

    private static HudProgram withoutLines(HudProgram program) {
        return new HudProgram(program.name(), program.ignoreAutoScale(), withoutLines(program.ops()),
                program.formatVersion());
    }

    private static List<HudOp> withoutLines(List<HudOp> ops) {
        return ops.stream().map(SchemaRoundTripTest::withoutLine).toList();
    }

    private static HudOp withoutLine(HudOp op) {
        return switch (op) {
            case HudOp.If block -> new HudOp.If(0, block.condition(), withoutLines(block.body()),
                    withoutLines(block.otherwise()), 0);
            case HudOp.Exit exit -> new HudOp.Exit(0);
            case HudOp.Call call -> new HudOp.Call(0, call.hud());
            case HudOp.Color color -> new HudOp.Color(0, color.argb());
            case HudOp.DrawString text -> new HudOp.DrawString(0, text.position(), text.format(), text.variables(),
                    text.centered());
            case HudOp.DrawRect rect -> new HudOp.DrawRect(0, rect.position(), rect.size());
            case HudOp.DrawLine line -> new HudOp.DrawLine(0, line.points(), line.striped(), line.pattern(),
                    line.factor());
            case HudOp.DrawTexture texture -> new HudOp.DrawTexture(0, texture.texture(), texture.position(),
                    texture.size(), texture.uvPosition(), texture.uvSize(), texture.rotation());
            case HudOp.DrawRadar radar -> new HudOp.DrawRadar(0, radar.entityRadar(), radar.position(), radar.size(),
                    radar.rotation());
            case HudOp.DrawGraduation scale -> new HudOp.DrawGraduation(0, scale.type(), scale.position(),
                    scale.rotation(), scale.roll());
            case HudOp.CameraRotation camera -> new HudOp.CameraRotation(0, camera.position());
        };
    }
}
