package com.ivomens.mcheli.content.pack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.File;

/**
 * The {@code mcheli_addons/generated} folder, where files produced at runtime are written. It is exposed to
 * the client as a resource pack but never scanned for content.
 */
public class GeneratedAddonPack extends AddonPack {

    private static final String FOLDER_NAME = "generated";
    private static final GeneratedAddonPack INSTANCE = new GeneratedAddonPack();

    private GeneratedAddonPack() {
        super("@generated", "Generated", "1.0", null, "EMB4-MCHeli", ImmutableList.of("EMB4", "Murachiki27"),
                "Generated addon(auto generate or update files)", "1", ImmutableMap.of());
    }

    public static GeneratedAddonPack instance() {
        return INSTANCE;
    }

    public static boolean isGeneratedAddonName(File file) {
        return FOLDER_NAME.equals(file.getName());
    }

    @Override
    public File getFile() {
        return new File(AddonManager.getAddonDir(), FOLDER_NAME);
    }
}
