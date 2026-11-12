package com.ivomens.mcheli.gametest.golden;

/**
 * The JSON header line of a golden file: everything needed to tell why two recordings differ
 * without opening the code. Only the data rows are compared; the header is for people.
 */
final class GoldenHeader {

    static final int SCHEMA = 1;

    private GoldenHeader() {
    }

    static String of(GoldenCase golden, TestTerrain terrain) {
        Script script = golden.script();
        return "{\"schema\":" + SCHEMA
                + ",\"type\":\"" + golden.type() + "\""
                + ",\"definition\":\"" + golden.definition() + "\""
                + ",\"definitionHash\":\"" + GoldenCase.definitionHash(golden.info().get()) + "\""
                + ",\"script\":\"" + script.name() + "\""
                + ",\"ticks\":" + script.ticks()
                + ",\"seed\":" + GoldenCase.SEED
                + ",\"dt\":\"control packets at level tick start; attitude " + golden.frames().perTick()
                + " frame(s) per tick of " + golden.frames().frameSeconds() + " s"
                + (golden.frames().fixedSteps() ? " at fixed substeps" : "") + "\""
                + ",\"terrain\":" + terrain.describe()
                + ",\"pinned\":" + PinnedSettings.describe()
                + ",\"commit\":\"" + GoldenMode.commit() + "\"}";
    }

    /** The definition hash a header names, or "unknown"; used to explain a failed comparison. */
    static String definitionHash(String header) {
        String key = "\"definitionHash\":\"";
        int start = header.indexOf(key);
        if (start < 0) {
            return "unknown";
        }
        start += key.length();
        return header.substring(start, header.indexOf('"', start));
    }
}
