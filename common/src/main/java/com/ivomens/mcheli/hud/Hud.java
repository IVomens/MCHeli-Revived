package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.content.registry.ContentEntry;
import com.ivomens.mcheli.hud.script.HudScript;
import com.ivomens.mcheli.hud.script.HudScriptCompiler;

/**
 * A HUD script loaded from {@code hud/*.txt} or {@code hud/*.yml}, compiled when it loads into the script the
 * client draws for the {@code SCRIPTED} HUD style. A HUD is content and nothing else; whatever changes while a
 * frame draws belongs to the renderer.
 *
 * @param name            the file name without extension, which seats and {@code Call} commands use
 * @param fileName        the path inside its pack, for messages
 * @param ignoreAutoScale draw at the design size even when the player enabled HUD auto scaling
 */
public record Hud(String name, String fileName, boolean ignoreAutoScale, HudScript script) {

    /** Placeholder for seats whose HUD is set to "none" or cannot be found. Draws nothing. */
    public static final Hud NoDisp = compile("none", "none", HudProgram.empty("none"));

    /** Compiles a HUD file that was just read; problems in its formulas are reported now, with the file. */
    public static Hud of(ContentEntry<HudProgram> entry) {
        return compile(entry.name(), entry.filePath(), entry.definition());
    }

    private static Hud compile(String name, String fileName, HudProgram program) {
        return new Hud(name, fileName, program.ignoreAutoScale(), HudScriptCompiler.compile(program, fileName));
    }

    @Override
    public String toString() {
        return "Hud{name='" + this.name + "', fileName='" + this.fileName + "', instructions="
                + this.script.code().size() + "}";
    }
}
