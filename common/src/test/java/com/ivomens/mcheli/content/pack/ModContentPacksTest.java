package com.ivomens.mcheli.content.pack;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where the packs of other mods load and what they are called. */
class ModContentPacksTest {

    @Test
    void modPacksLoadBetweenTheBundledContentAndThePlayersPacks() {
        File bundled = new File(BuiltinAddonPack.EXTRACTED_FOLDER_NAME);
        File players = new File("a_pack");
        File zz = new File("zz_overrides");
        File[] addonFolder = {bundled, players, zz};
        List<File> mods = List.of(new File("alpha"), new File("beta"));

        assertEquals(List.of(bundled, mods.get(0), mods.get(1), players, zz),
                PackOrder.withModPacks(addonFolder, mods));
    }

    @Test
    void aModsFirstPackIsNamedAfterTheModAndTheOthersAfterTheirFolder() {
        ModContentPacks.Registration first = new ModContentPacks.Registration("example_mod", "mcheli_pack");
        ModContentPacks.Registration second = new ModContentPacks.Registration("example_mod", "extra/Planes");

        assertEquals("example_mod", ModContentPacks.folderName(first, 1));
        assertEquals("example_mod.extra_planes", ModContentPacks.folderName(second, 2));
    }
}
