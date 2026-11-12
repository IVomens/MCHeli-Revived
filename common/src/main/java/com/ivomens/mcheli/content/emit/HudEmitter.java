package com.ivomens.mcheli.content.emit;

import com.ivomens.mcheli.core.content.def.hud.HudCommand;
import com.ivomens.mcheli.core.content.def.hud.HudCommands;
import com.ivomens.mcheli.core.content.def.hud.HudOp;
import com.ivomens.mcheli.core.content.def.hud.HudProgram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a HUD program as YAML through the command table: one list item per command, each command's keys
 * through its entry schema, formulas exactly as the source wrote them. A format 2 program starts with its
 * {@code SchemaVersion} and writes its blocks as {@code If}/{@code Then}/{@code Else}; the first format keeps
 * {@code If}/{@code Do}. Reading the output gives the same program, apart from the line numbers.
 */
public final class HudEmitter {

    private HudEmitter() {
    }

    public static String emit(HudProgram program) {
        List<Object> items = new ArrayList<>();
        if (program.nestsBlocks()) {
            items.add(Map.of(HudCommands.SCHEMA_VERSION, program.formatVersion()));
        }
        if (program.ignoreAutoScale()) {
            items.add(Map.of(HudCommands.IGNORE_AUTO_SCALE.getFirst(), true));
        }
        items.addAll(commands(program.ops(), program));
        return SchemaEmitter.write(items);
    }

    private static List<Object> commands(List<HudOp> ops, HudProgram program) {
        String name = program.name();
        List<Object> items = new ArrayList<>(ops.size());
        for (HudOp op : ops) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (op instanceof HudOp.If block) {
                item.put("If", block.condition());
                item.put(program.nestsBlocks() ? "Then" : "Do", commands(block.body(), program));
                if (!block.otherwise().isEmpty()) {
                    item.put("Else", commands(block.otherwise(), program));
                }
            } else {
                HudCommand<?> command = HudCommands.of(op);
                item.put(command.key(), arguments(command, op, name));
            }
            items.add(item);
        }
        return items;
    }

    /**
     * The command's map of keys, or the plain value of its short form. A command without arguments is
     * written with its own name, {@code Exit: exit}, since YAML needs some value.
     */
    private static <O extends HudOp> Object arguments(HudCommand<O> command, HudOp op, String name) {
        if (command.takesNoArguments()) {
            return command.key().toLowerCase(Locale.ROOT);
        }
        return SchemaEmitter.entry(command.arguments(), command.type().cast(op), name);
    }
}
