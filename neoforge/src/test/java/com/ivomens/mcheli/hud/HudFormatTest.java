package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.content.bind.HudYamlReader;
import com.ivomens.mcheli.core.content.def.hud.HudOp;
import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.content.emit.HudEmitter;
import com.ivomens.mcheli.golden.MinecraftBootstrap;
import com.ivomens.mcheli.hud.script.HudInstruction;
import com.ivomens.mcheli.hud.script.HudScript;
import com.ivomens.mcheli.hud.script.HudScriptCompiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two HUD formats: gates for every file without a version, nested blocks for {@code SchemaVersion: 2}. */
class HudFormatTest {

    private static final List<String> NESTED = List.of(
            "- SchemaVersion: 2",
            "- If: altitude > 10",
            "  Then:",
            "  - If: is_uav",
            "    Then:",
            "    - Exit: exit",
            "    Else:",
            "    - Call: other",
            "  Else:",
            "  - Call: low");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.ensure();
    }

    @Test
    void formatTwoNestsBlocksWithElse() {
        HudProgram program = HudYamlReader.read("nested", "nested.yml", NESTED);
        assertEquals(HudProgram.FORMAT_NESTED, program.formatVersion());
        HudOp.If outer = assertInstanceOf(HudOp.If.class, program.ops().getFirst());
        HudOp.If inner = assertInstanceOf(HudOp.If.class, outer.body().getFirst());
        assertEquals(List.of(new HudOp.Exit(6)), inner.body());
        assertEquals(List.of(new HudOp.Call(8, "other")), inner.otherwise());
        assertEquals(List.of(new HudOp.Call(10, "low")), outer.otherwise());
        assertEquals(2, outer.line(), "commands keep their YAML line");
    }

    @Test
    void formatTwoEmitsAndReadsBack() {
        HudProgram program = HudYamlReader.read("nested", "nested.yml", NESTED);
        String yaml = HudEmitter.emit(program);
        assertTrue(yaml.startsWith("- SchemaVersion: 2"), yaml);
        HudProgram again = HudYamlReader.read("nested", "nested.yml", yaml.lines().toList());
        assertEquals(program.ops().size(), again.ops().size());
        assertEquals(HudEmitter.emit(program), HudEmitter.emit(again));
    }

    @Test
    void formatTwoLowersToJumps() {
        HudScript script = HudScriptCompiler.compile(HudYamlReader.read("nested", "nested.yml", NESTED), "nested.yml");
        List<HudInstruction> code = script.code();
        // outer branch, inner branch, exit, jump over inner else, call other, jump over outer else, call low
        HudInstruction.Branch outer = assertInstanceOf(HudInstruction.Branch.class, code.get(0));
        HudInstruction.Branch inner = assertInstanceOf(HudInstruction.Branch.class, code.get(1));
        assertInstanceOf(HudInstruction.Exit.class, code.get(2));
        assertEquals(5, assertInstanceOf(HudInstruction.Jump.class, code.get(3)).target());
        assertEquals(4, inner.target());
        assertEquals(7, assertInstanceOf(HudInstruction.Jump.class, code.get(5)).target());
        assertEquals(6, outer.target());
        assertEquals(7, code.size());
    }

    @Test
    void theFirstFormatKeepsGates() {
        HudProgram program = HudYamlReader.read("gates", "gates.yml",
                List.of("- If: altitude > 10", "  Do:", "  - Exit: exit"));
        assertEquals(HudProgram.FORMAT_GATES, program.formatVersion());
        List<HudInstruction> code = HudScriptCompiler.compile(program, "gates.yml").code();
        assertInstanceOf(HudInstruction.Gate.class, code.get(0));
        assertInstanceOf(HudInstruction.Exit.class, code.get(1));
        assertInstanceOf(HudInstruction.EndGate.class, code.get(2));
    }
}
