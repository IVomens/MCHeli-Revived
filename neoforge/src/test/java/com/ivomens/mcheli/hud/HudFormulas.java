package com.ivomens.mcheli.hud;

import com.ivomens.mcheli.core.content.def.hud.HudProgram;
import com.ivomens.mcheli.content.registry.LoadedContent;
import com.ivomens.mcheli.golden.content.ContentPipeline;
import com.ivomens.mcheli.golden.content.LegacyHudView.LegacyFormula;
import com.ivomens.mcheli.golden.content.LegacyHudView;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Loads the HUDs a test draws and collects the formulas they contain. */
final class HudFormulas {

    private HudFormulas() {
    }

    /**
     * The bundled HUDs, the TXT corpus HUD and the HUD test scripts, by pack name and file, parsed
     * with the real pipeline. Loading also installs the HUD registry, so {@code Call} finds them.
     */
    static Map<String, LoadedContent<HudProgram, Hud>> loadHuds() {
        Map<String, LoadedContent<HudProgram, Hud>> huds = new TreeMap<>();
        Map<Path, Map<String, LoadedContent<?, ?>>> packs = ContentPipeline.loadEach(ContentPipeline.bundledPack(),
                ContentPipeline.testPack("txt"), ContentPipeline.testPack("hud"));
        packs.forEach((pack, files) -> files.forEach((file, loaded) -> {
            if (loaded.value() instanceof Hud) {
                huds.put(packName(pack) + "/" + file.substring(file.lastIndexOf('/') + 1),
                        HudManager.registry().cast(loaded));
            }
        }));
        return huds;
    }

    private static String packName(Path pack) {
        return pack.equals(ContentPipeline.bundledPack()) ? "bundled" : pack.getFileName().toString();
    }

    /**
     * Every distinct formula of the given HUDs by the text Aviator was given for it, with the texts the compiler
     * reads for it: more than one where scripts spelled the same test both ways ({@code is_uav==0}, {@code !is_uav}).
     */
    static Map<String, TreeSet<String>> collect(Iterable<LoadedContent<HudProgram, Hud>> huds) {
        Map<String, TreeSet<String>> formulas = new TreeMap<>();
        for (LoadedContent<HudProgram, Hud> hud : huds) {
            for (LegacyFormula formula : LegacyHudView.formulas(hud.entry())) {
                formulas.computeIfAbsent(formula.aviatorText(), text -> new TreeSet<>()).add(formula.compilerText());
            }
        }
        return formulas;
    }
}
