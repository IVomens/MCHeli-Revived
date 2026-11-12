package com.ivomens.mcheli.fabric;

import com.google.common.collect.MapMaker;
import com.ivomens.mcheli.platform.PackHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Vanilla pack metadata has no "hidden" flag. The metadata made here is remembered (weakly, by identity),
 * and the pack mixins leave packs carrying it out of the same lists NeoForge leaves hidden packs out
 * of: the saved pack lists, the pack selection screen and {@code /datapack list enabled}.
 */
public class FabricPackHelper implements PackHelper {

    private static final Set<Pack.Metadata> HIDDEN = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());

    @Override
    public Pack.Metadata hiddenPackMetadata(Component description) {
        Pack.Metadata metadata = new Pack.Metadata(description, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        HIDDEN.add(metadata);
        return metadata;
    }

    /** True for metadata made by {@link #hiddenPackMetadata}. */
    public static boolean isHidden(Pack.Metadata metadata) {
        return HIDDEN.contains(metadata);
    }
}
