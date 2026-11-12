package com.ivomens.mcheli.neoforge;

import com.ivomens.mcheli.platform.PackHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.flag.FeatureFlagSet;

import java.util.List;

public class NeoForgePackHelper implements PackHelper {

    @Override
    public Pack.Metadata hiddenPackMetadata(Component description) {
        return new Pack.Metadata(description, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), true);
    }
}
